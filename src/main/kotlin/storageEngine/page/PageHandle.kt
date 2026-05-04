package storageEngine.page

import storageEngine.BufferPoolManager
import java.nio.ByteBuffer

class PageLock(
    val frame: Frame,
    private val bufferPoolManager: BufferPoolManager
): AutoCloseable {

    private var isReadLocked = false
    private var isWriteLocked = false

    fun lockRead(){
        frame.latch.readLock().lock()
    }

    fun lockWrite(){
        frame.latch.writeLock().lock()
    }

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

}
