package storageEngine.page

import storageEngine.BufferPoolManager
import java.nio.ByteBuffer

/**
 * A held claim on one [Frame]: the pin that keeps [BufferPoolManager] from evicting it, plus
 * (once acquired) the read or write side of [Frame.latch]. Returned by [BufferPoolManager]'s
 * `fetchPage`/`newPage` already pinned and locked in the requested mode — this class does not
 * acquire the initial lock itself, only manages its lifetime afterwards ([unlock],
 * [downgradeLock]) and releases everything on [close].
 *
 * Callers own the lock's lifetime explicitly: nothing here auto-unlocks on scope exit except via
 * [close] ([AutoCloseable]), which unlocks the latch and unpins the frame. Every fetched page must
 * eventually be closed, directly or via [index.btree.LockManager]/[index.btree.Cursor] — an
 * unclosed `PageLock` leaks a pin (and, if still locked, a latch) on its frame.
 * */
class PageLock(
    @PublishedApi internal val frame: Frame,
    private val bufferPoolManager: BufferPoolManager,
    isReadLocked: Boolean = false,
    isWriteLocked: Boolean = false
): AutoCloseable {
    var isReadLocked: Boolean = isReadLocked
        private set

    var isWriteLocked: Boolean = isWriteLocked
        private set

    val frameId: Int
        get() = frame.frameId

    val pageId: Long
        get() = frame.pageId.get()

    /** Releases whichever latch ([isReadLocked]/[isWriteLocked]) is currently held, without unpinning. */
    fun unlock(){
        if(isReadLocked) frame.latch.readLock().unlock()
        if(isWriteLocked) frame.latch.writeLock().unlock()
        isReadLocked = false
        isWriteLocked = false
    }

    /** [unlock]s, then unpins the frame (marking it dirty in the buffer pool if [asWriteView] was ever used). */
    override fun close() {
        unlock()
        bufferPoolManager.unpinPage(frame.pageId.get(), isDirty)
    }

    /**
     * Made internal (not private) so the inline functions below can access it,
     * but @PublishedApi keeps it looking private from outside this module.
     * */
    @PublishedApi
    internal var isDirty: Boolean = false

    /**
     * Read-only access to the frame's bytes.
     * Does not itself acquire a lock — the caller is expected to already hold read or write.
     * */
    inline fun <T> asReadView(viewFactory: (ByteBuffer) -> T): T {
        return viewFactory(frame.data)
    }

    /**
     * Mutable access to the frame's bytes;
     * Marks the frame dirty so [close] flushes it back to the buffer pool as modified.
     * Requires the write latch already be held.
     * */
    inline fun <T> asWriteView(viewFactory: (ByteBuffer) -> T): T {
        this.isDirty = true
        return viewFactory(frame.data)
    }

    /** Marks the frame dirty without going through [asWriteView] (e.g. after a raw buffer mutation). */
    fun setDirty(){
        this.isDirty = true
    }

    /**
     * Converts a held write lock to a read lock, for a descent that took WRITE but turned out to
     * only need to read this node. No-op if [isWriteLocked] is false.
     *
     * **Not atomic**: releases the write lock, then separately acquires the read lock — another
     * writer can acquire the latch in between. The frame stays pinned throughout, so it can't be
     * evicted, but this differs from the atomic write-held-while-acquiring-read strategy the
     * latch-crabbing design called for. See issue #59.
     * */
    fun downgradeLock(){
        if(isWriteLocked){
            frame.latch.writeLock().unlock()
            frame.latch.readLock().lock()
            isWriteLocked = false
            isReadLocked = true
        }
    }
}
