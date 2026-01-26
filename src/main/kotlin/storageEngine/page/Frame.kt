package storageEngine.page

import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock

class Frame(
    val frameId: Int,
    pageSize: Int
){
    val data: ByteBuffer = ByteBuffer.allocateDirect(pageSize)
    val latch = ReentrantReadWriteLock()
    @Volatile var pinCount: Int = 0
    @Volatile var isDirty: Boolean = false

    fun reset(){
        pinCount = 0
        isDirty = false
        data.clear()
    }
}