package storageEngine.page

import util.INVALID_PAGE_ID
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * One slot of [storageEngine.BufferPoolManager]'s buffer pool array. Holds exactly one page's
 * worth of bytes at a time; which page (if any) is tracked by [pageId] and can change whenever
 * [pinCount] is 0 (the frame is eviction-eligible).
 *
 * @property pageId The disk page currently loaded here, or [INVALID_PAGE_ID] if this frame has
 *   never been used / was just evicted. `AtomicLong` so [storageEngine.BufferPoolManager] can set
 *   it before [reset] under the frame's write latch (see
 *   [storageEngine.BufferPoolManager.fetchPage]).
 * */
class Frame(
    val frameId: Int,
    val pageId: AtomicLong = AtomicLong(INVALID_PAGE_ID),
    pageSize: Int
){
    val data: ByteBuffer = ByteBuffer.allocateDirect(pageSize)
    val latch = ReentrantReadWriteLock()
    val pinCount: AtomicInteger = AtomicInteger(0)
    val isDirty: AtomicBoolean = AtomicBoolean(false)

    /**
     * Clears [isDirty] and resets [data]'s position/limit for a fresh read — this does **not**
     * zero out the buffer's actual bytes, it only rewinds the `ByteBuffer` view (`clear()`
     * semantics). The stale content is overwritten by the caller's subsequent
     * [storageEngine.DiskManager.readPage]/`initData()` before anyone reads it.
     * */
    fun reset(){
        isDirty.set(false)
        data.clear()
    }
}
