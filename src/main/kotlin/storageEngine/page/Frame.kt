package storageEngine.page

import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Frame(
    val frameId: Int,
    val pageId: AtomicLong = AtomicLong(-1L),
    pageSize: Int
){
    val data: ByteBuffer = ByteBuffer.allocateDirect(pageSize)
    val latch = ReentrantReadWriteLock()
    val pinCount: AtomicInteger = AtomicInteger(0)
    val isDirty: AtomicBoolean = AtomicBoolean(false)

    fun reset(){
        isDirty.set(false)
        data.clear()
    }
}
