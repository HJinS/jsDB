package storageEngine.frameManagement

import java.nio.ByteBuffer

class Frame(
    val frameId: Int,
    pageSize: Int,
    val pageId: Long?,
    var pinCount: Int = 0,
){
    val data: ByteBuffer = ByteBuffer.allocateDirect(pageSize)
}