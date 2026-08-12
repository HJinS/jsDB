package storageEngine.page

import storageEngine.BufferPoolManager
import java.nio.ByteBuffer

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

    fun unlock(){
        if(isReadLocked) frame.latch.readLock().unlock()
        if(isWriteLocked) frame.latch.writeLock().unlock()
        isReadLocked = false
        isWriteLocked = false
    }

    override fun close() {
        unlock()
        bufferPoolManager.unpinPage(frame.pageId.get(), isDirty)
    }

    // internal로 바꾸되, 외부에서는 private처럼 보이도록 인라인만 허용
    @PublishedApi
    internal var isDirty: Boolean = false

    inline fun <T> asReadView(viewFactory: (ByteBuffer) -> T): T {
        return viewFactory(frame.data)
    }

    inline fun <T> asWriteView(viewFactory: (ByteBuffer) -> T): T {
        this.isDirty = true
        return viewFactory(frame.data)
    }

    fun setDirty(){
        this.isDirty = true
    }
    
    fun downgradeLock(){
        if(isWriteLocked){
            frame.latch.writeLock().unlock()
            frame.latch.readLock().lock()
            isWriteLocked = false
            isReadLocked = true
        }
    }
}
