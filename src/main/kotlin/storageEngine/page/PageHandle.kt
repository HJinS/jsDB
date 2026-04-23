package storageEngine.page

import storageEngine.BufferPoolManager
import java.nio.ByteBuffer

class PageHandle(
    val frame: Frame,
    private val bufferPoolManager: BufferPoolManager
): AutoCloseable {

    // internal로 바꾸되, 외부에서는 private처럼 보이도록 인라인만 허용
    @PublishedApi
    internal var isDirty: Boolean = false

    inline fun <T> asReadView(viewFactory: (ByteBuffer) -> T): T {
        frame.latch.readLock().lock()
        try{
            return viewFactory(frame.data)
        } finally {
            frame.latch.readLock().unlock()
        }

    }

    inline fun <T> asWriteView(viewFactory: (ByteBuffer) -> T): T {
        this.isDirty = true
        frame.latch.writeLock().lock()
        try{
            return viewFactory(frame.data)
        } finally {
            frame.latch.writeLock().unlock()
        }
    }

    fun setDirty(){
        this.isDirty = true
    }

    override fun close() {
        bufferPoolManager.unpinPage(frame.pageId, isDirty)
    }
}
