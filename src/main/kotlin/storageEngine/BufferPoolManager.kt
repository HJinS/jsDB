package storageEngine

import config.IndexConfig
import index.btree.node.InternalNode
import index.serializer.MultiColumnKeySerializer
import storageEngine.DiskManager
import storageEngine.lru.MidpointLRUPolicy
import storageEngine.page.Frame
import storageEngine.page.Page
import storageEngine.page.PageLock
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
import storageEngine.util.LockMode
import storageEngine.exception.BufferPoolManagerException 
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock

/**
 * frame table
 * - pageID, frameID 매핑
 * - pageID 로부터 frameID 를 얻어내기 위함
 * buffer pool
 * - frameID 를 가지고 실제 frame 을 보관
 * node map
 * - lru 를 관리하기 위함
 * - frame 접근을 바로 하기 위함
 * doubly linked list
 * - lru linked list
 * - new, old 로 나뉘어 져 있음
 *   - midpoint 를 두어 new, old 를 구분
 *   - old - 37, new 63
 *
 *
 * 주요 기능
 * - 페이지 캐싱
 * - fetchPage
 * - 페이지 교체
 * - dirty page 관리
 *   - write on eviction
 *   - write on shutdown
 *   - background thread
 *
 *
 * BufferPool
 * Frame
 * LRU
 * StorageManager
 * */


class BufferPoolManager(
    private val diskManager: DiskManager,
    private val replacer: MidpointLRUPolicy,
    private val indexConfig: IndexConfig,
    poolSize: Int
){
    private val frames: Array<Frame> = Array(poolSize) { Frame(frameId=it, pageSize=indexConfig.pageSize) }
    private val pageTable = HashMap<Long, Int>()
    private val freeList = ArrayDeque<Int>()
    private val globalLatch = ReentrantLock()

    /**
     * page 있는지 여부 확인
     * 있으면 -> 사용 표시 + pin
     * 없으면 -> 빈 프레임 확보
     *   -> 기존 페이지가 Dirty이면 disk write
     *   -> 메타데이터 초기화 및 매핑업데이트
     *   -> disk manager 데이터 읽어오기
     *   -> pin
     *   -> 새로운 매핑 등록
     * freeFrameId의 경우에는 LRU 알고리즘 사용
     * */
    fun fetchPage(pageId: Long, lockMode: LockMode): PageLock{
        var victimPageId: Long? = null
        var needIO = false
        var frameId: Int = -1
        var frame: Frame? = null
        var isReadLocked: Boolean = false
        var isWriteLocked: Boolean = false


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
                // writeLock을 여기서 미리 잡아둠. 밑에서 IO 작업 진행 필요
                frame.latch.writeLock().lock()
                isReadLocked = false
                isWriteLocked = true
                val currentPageId = frame.pageId.get()
                if(frame.isDirty.get() && currentPageId != -1L){
                    victimPageId = currentPageId
                }
                pageTable.remove(currentPageId)
                frame.pinCount.set(1)
                pageTable[pageId] = frameId
                replacer.pin(frameId)
            }
        } catch(e: Exception){
            globalLatch.unlock()
            throw BufferPoolManagerException.UnExpectedException(pageId, e)
        }
        if(needIO){
            try{
                if(victimPageId != null){
                    diskManager.writePage(victimPageId, frame.data)
                }
                // pageId 설정하는 부분이랑 reset은 정합성을 위해 writelock 안에서 수행
                frame.pageId.set(pageId)
                frame.reset()
                diskManager.readPage(pageId, frame.data)
            } catch(e: Exception) {
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
     * 1. 새로운 PageID 할당(pageID 관리는 보통 disk manager 가 관리함)
     * 2. 빈 Frame 탐색 -> 빈 프레임이 없으면 eviction 실행 후 공간 확보(disk flush 필요.)
     * 3. pageTable 업데이트, page pin, dirty 마킹, 페이지 초기화(헤더 등)
     * */
    fun newPage(pageId: Long): PageLock{
        var frame: Frame? = null
        var frameId: Int = -1
        var victimPageId: Long? = null

        globalLatch.lock()
        try{
            frameId = getFreeFrameId()
            frame = frames[frameId]
            frame.latch.writeLock().lock()
            val currentPageId = frame.pageId.get()
            if(frame.isDirty.get() && currentPageId != -1L){
                victimPageId = currentPageId
            }
            pageTable.remove(currentPageId)
            pageTable[pageId] = frameId
            frame.pinCount.set(1)
            replacer.pin(frameId)
        } catch(e: Exception){
            globalLatch.unlock()
            throw BufferPoolManagerException.UnExpectedException(pageId, e)
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
            throw BufferPoolManagerException.UnExpectedException(pageId, e)
        }
        return PageLock(frame, this, false, true)
    }

    /**
     * page 사용 종료시 lock에서 호출
     * frame 가져와서 -> pinCount -- -> isDirty 몇 표기
     *
     * */
    fun unpinPage(pageId: Long, isDirty: Boolean){
        val frame: Frame
        val frameId: Int

        globalLatch.lock()
        try{
            frameId = pageTable[pageId] ?: throw BufferPoolManagerException.PageNotFoundInCacheException(pageId)
            frame = frames[frameId]
            if(frame.pinCount.get() <= 0) return
            val pinCount = frame.pinCount.decrementAndGet()
            if(isDirty) frame.isDirty.set(true)
            if(pinCount == 0) replacer.unpin(frameId)
        } finally{
            globalLatch.unlock()
        }
    }

    fun flushPage(pageId: Long){
        val frame: Frame
        val frameId: Int
        globalLatch.lock() 
        try{
            frameId = pageTable[pageId] ?: throw BufferPoolManagerException.PageNotFoundInCacheException(pageId)
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

    fun deletePage(pageId: Long){
        val frame: Frame
        val frameId: Int
        globalLatch.lock()
        try{
            frameId = pageTable[pageId] ?: return
            frame = frames[frameId]
            if(frame.pinCount.get() > 0) throw BufferPoolManagerException.PageInUseException(pageId)
            frame.latch.writeLock().lock()
            try{
                pageTable.remove(pageId)
                frame.pageId.set(-1L)
                frame.pinCount.set(0)
                frame.reset()
                replacer.unpin(frameId)
            } finally{
                frame.latch.writeLock().unlock()
            }
        } finally{
            globalLatch.unlock()
        }

    }

    private fun getFreeFrameId() = if(freeList.isEmpty()) replacer.evict() else freeList.removeFirst()

}
