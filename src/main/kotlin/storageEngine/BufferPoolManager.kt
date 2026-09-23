package storageEngine

import config.IndexConfig
import storageEngine.lru.FrameNodePolicy
import storageEngine.lru.ReplacementPolicy
import storageEngine.page.Frame
import storageEngine.page.PageLock
import util.LockMode
import exception.StorageEngineException
import util.EngineErrorDetail
import util.INVALID_PAGE_ID
import java.util.concurrent.locks.ReentrantLock

/**
 * Buffer pool caching disk pages into an in-memory [Frame] array.
 * - [pageTable]: pageId -> frameId mapping (which page lives in which frame).
 * - [frames]: fixed-size array (`poolSize` entries) actually holding the data.
 * - [freeList]: ids of frames never yet used. Once exhausted, a frame is freed via [replacer]
 *   (Midpoint LRU, [FrameNodePolicy]) eviction instead.
 * - [globalLatch]: protects shared structures like [pageTable]/[freeList] only — never held across
 *   disk I/O, which is instead guarded by the per-frame [Frame.latch].
 *
 * Main responsibilities
 * - Page caching/replacement: [fetchPage], [newPage] (on a cache miss, [replacer] picks a victim
 *   frame; a dirty victim is written back — write-on-eviction).
 * - Dirty page management: [flushPage] (write-on-eviction and explicit flush only — there's no
 *   write-on-shutdown or background flush thread yet, see issue #47).
 * - Page reclamation: [deletePage].
 *
 * Known exception-safety gap (issue #58): if I/O fails inside [fetchPage]/[newPage], the
 * [pageTable] entry and [Frame.pinCount] already registered are not rolled back.
 * */


class BufferPoolManager(
    private val diskManager: DiskManager,
    private val replacer: ReplacementPolicy,
    private val indexConfig: IndexConfig,
    poolSize: Int
){
    private val frames: Array<Frame> = Array(poolSize) { Frame(frameId=it, pageSize=indexConfig.pageSize) }
    private val pageTable = HashMap<Long, Int>()
    private val freeList = ArrayDeque<Int>(List(poolSize) {it})
    private val globalLatch = ReentrantLock()

    /**
     * Checks whether the page is already cached.
     * If so -> mark in-use + pin.
     * If not -> secure an empty frame
     *   -> if the evicted page was dirty, write it to disk
     *   -> reset metadata and update the mapping
     *   -> read the data via the disk manager
     *   -> pin
     *   -> register the new mapping
     * An empty frame id comes from the LRU algorithm.
     *
     * Known gap (#58): if the I/O (`readPage`) below fails, the `catch` only releases the write
     * lock and rethrows — the [pageTable] entry and [Frame.pinCount] already registered before
     * that point are not rolled back.
     * */
    fun fetchPage(pageId: Long, lockMode: LockMode): PageLock{
        var victimPageId: Long? = null
        var needIO = false
        var frameId: Int
        var frame: Frame?
        var isReadLocked = false
        var isWriteLocked = false


        globalLatch.lock()
        try{
            if(pageTable.containsKey(pageId)) {
                frameId = pageTable[pageId]!!
                frame = frames[frameId]
                replacer.add(frameId)
                replacer.pin(frameId)
                frame.pinCount.incrementAndGet()
            } else {
                frameId = getFreeFrameId()
                frame = frames[frameId]
                needIO = true
                // Acquire the write lock here ahead of time; I/O happens below.
                frame.latch.writeLock().lock()
                isReadLocked = false
                isWriteLocked = true
                val currentPageId = frame.pageId.get()
                if(frame.isDirty.get() && currentPageId != INVALID_PAGE_ID){
                    victimPageId = currentPageId
                }
                pageTable.remove(currentPageId)
                frame.pinCount.set(1)
                pageTable[pageId] = frameId
                replacer.pin(frameId)
            }
        } catch(e: Exception){
            throw StorageEngineException.UnExpected(
                EngineErrorDetail(
                    pageId = pageId,
                    reason = "Something went wrong. Maybe buffer full exhausted."
                ), e
            )
        }finally {
            globalLatch.unlock()
        }
        if(needIO){
            try{
                if(victimPageId != null){
                    diskManager.writePage(victimPageId, frame.data)
                }
                // Setting pageId and resetting happen inside the write lock, for consistency.
                frame.pageId.set(pageId)
                frame.reset()
                diskManager.readPage(pageId, frame.data)
            } catch (e: Exception) {
                frame.latch.writeLock().unlock()
                throw e
            }
            if(lockMode == LockMode.READ){
                frame.latch.writeLock().unlock()
                frame.latch.readLock().lock()
                isReadLocked = true
                isWriteLocked = false
            }
        } else {
            if(lockMode == LockMode.READ){
                frame.latch.readLock().lock()
                isReadLocked = true
                isWriteLocked = false
            }
            else{
                frame.latch.writeLock().lock()
                isReadLocked = false
                isWriteLocked = true

            }
        }
        return PageLock(frame, this, isReadLocked, isWriteLocked)
    }


    /**
     * 1. Allocate a new page id (page id management is usually the disk manager's job).
     * 2. Find an empty frame -> if none, run eviction first to make room (needs a disk flush).
     * 3. Update [pageTable], pin the page, mark it dirty, initialize the page (header, etc.).
     *
     * Known gap (#58): if writing the victim page (`writePage`) fails, only the write lock is
     * released before rethrowing — the [pageTable] entry and [Frame.pinCount] already registered
     * are not rolled back.
     * */
    fun newPage(pageId: Long): PageLock{
        var frame: Frame?
        var frameId: Int
        var victimPageId: Long? = null

        globalLatch.lock()
        try{
            frameId = getFreeFrameId()
            frame = frames[frameId]
            frame.latch.writeLock().lock()
            val currentPageId = frame.pageId.get()
            if(frame.isDirty.get() && currentPageId != INVALID_PAGE_ID){
                victimPageId = currentPageId
            }
            pageTable.remove(currentPageId)
            pageTable[pageId] = frameId
            frame.pinCount.set(1)
            replacer.pin(frameId)
        } catch(e: Exception){
            throw StorageEngineException.UnExpected(
                EngineErrorDetail(
                    pageId = pageId,
                    reason = "Something went wrong. Maybe buffer full exhausted."
                ), e
            )
        }finally {
            globalLatch.unlock()
        }
        try{
            if(victimPageId != null){
                diskManager.writePage(victimPageId, frame.data)
            }
            frame.apply {
                reset()
                isDirty.set(true)
                this.pageId.set(pageId)
            }
        } catch(e: Exception){
            frame.latch.writeLock().unlock()
            throw StorageEngineException.UnExpected(
                EngineErrorDetail(
                    pageId = pageId,
                    reason = "Something went wrong. Maybe buffer full exhausted."
                ),
                e
            )
        }
        return PageLock(frame, this, false, true)
    }

    /**
     * Called from [PageLock.close] when a page is done being used.
     * Looks up the frame -> decrements pinCount -> records [isDirty] (only ever set to true here;
     * never cleared back to false by this call).
     * */
    fun unpinPage(pageId: Long, isDirty: Boolean){
        val frame: Frame
        val frameId: Int

        globalLatch.lock()
        try{
            frameId = pageTable[pageId]
                ?: throw StorageEngineException.PageNotFoundInCache(
                    EngineErrorDetail(
                        pageId = pageId,
                        reason = "Unable to find page in buffer pool"
                    )
                )
            frame = frames[frameId]
            if(frame.pinCount.get() <= 0) return
            val pinCount = frame.pinCount.decrementAndGet()
            if(isDirty) frame.isDirty.set(true)
            if(pinCount == 0) replacer.unpin(frameId)
        } finally{
            globalLatch.unlock()
        }
    }

    /**
     * If [pageId] is dirty, writes the frame's current content to disk and clears the dirty flag.
     * Does nothing if it's already clean. Only briefly holds the frame's read lock, so it can run
     * concurrently with other readers while excluding writers.
     * */
    fun flushPage(pageId: Long){
        val frame: Frame
        val frameId: Int
        globalLatch.lock() 
        try{
            frameId = pageTable[pageId]
                ?: throw StorageEngineException.PageNotFoundInCache(
                    EngineErrorDetail(
                        pageId = pageId,
                        reason = "Unable to find page in buffer pool"
                    )
                )
            frame = frames[frameId]
            frame.latch.readLock().lock()
        } finally{
            globalLatch.unlock()
        }
        try{
            val pageId: Long = frame.pageId.get()
            if(frame.isDirty.get()){
                diskManager.writePage(pageId, frame.data)
                frame.isDirty.set(false)
            }
        } finally{
            frame.latch.readLock().unlock()
        }
    }

    /**
     * Reclaims [pageId] immediately if it's in the buffer pool (does nothing if it isn't cached —
     * registering it on the disk free list is [StorageManager.deletePage]'s job, done before this
     * is called). Throws [StorageEngineException.PageInUse] if it's still pinned
     * ([Frame.pinCount] > 0) — flushing is the caller's responsibility, so dirtiness isn't checked
     * here.
     * */
    fun deletePage(pageId: Long){
        val frame: Frame
        val frameId: Int
        globalLatch.lock()
        try{
            frameId = pageTable[pageId] ?: return
            frame = frames[frameId]
            if(frame.pinCount.get() > 0)
                throw StorageEngineException.PageInUse(
                    EngineErrorDetail(
                        pageId = pageId,
                        reason = "Page is currently in use (pin count > 0) and cannot be deleted."
                    )
                )
            frame.latch.writeLock().lock()
            try{
                pageTable.remove(pageId)
                frame.pageId.set(INVALID_PAGE_ID)
                frame.pinCount.set(0)
                frame.reset()
                freeList.add(frameId)
            } finally{
                frame.latch.writeLock().unlock()
            }
        } finally{
            globalLatch.unlock()
        }

    }

    /** Total page count on disk (regardless of caching status; delegates to [DiskManager]). */
    fun getNumPages() = diskManager.getNumPages()

    private fun getFreeFrameId() = if(freeList.isEmpty()) replacer.evict() else freeList.removeFirst()

}
