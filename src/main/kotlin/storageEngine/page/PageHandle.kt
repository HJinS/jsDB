package storageEngine.page

import BufferPoolManager
import java.nio.ByteBuffer

class PageHandle(
    private val frame: Frame,
    private val bufferPoolManager: BufferPoolManager
): AutoCloseable {

    fun <T> asView(viewFactory: (ByteBuffer) -> T): T {
        return viewFactory(frame.data)
    }

    override fun close() {
        bufferPoolManager.unpinPage()
    }

}