package storageEngine.page

import storageEngine.BufferPoolManager
import java.nio.ByteBuffer

class PageHandle(
    val frame: Frame,
    private val bufferPoolManager: BufferPoolManager
): AutoCloseable {

    inline fun <T> asReadView(viewFactory: (ByteBuffer) -> T): T {
        frame.latch.readLock().lock()
        try{
            return viewFactory(frame.data)
        } finally {
            frame.latch.readLock().unlock()
        }

    }

    inline fun <T> asWriteView(viewFactory: (ByteBuffer) -> T): T {
        frame.latch.writeLock().lock()
        try{
            return viewFactory(frame.data)
        } finally {
            frame.latch.writeLock().unlock()
        }
    }

    override fun close() {
        bufferPoolManager.unpinPage()
    }
}