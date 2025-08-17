package storageEngine

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class DiskManager(dbPath: String, private val pageSize: Int) {

    private val fileChannel: FileChannel = RandomAccessFile(dbPath, "rw").channel

    /**
     * 특정 페이지 ID에 해당하는 페이지 데이터를 디스크에서 읽어옵니다.
     * @param pageId 읽어올 페이지의 ID
     * @param pageData 읽어온 데이터를 채울 ByteArray (페이지 크기와 동일)
     */
    fun readPage(pageId: Long, pageData: ByteArray){
        val offset = pageId * pageSize

        val buffer = ByteBuffer.wrap(pageData)
        fileChannel.read(buffer, offset)
    }

    /**
     * 특정 페이지 ID 위치에 페이지 데이터를 디스크에 씁니다.
     * @param pageId 데이터를 쓸 페이지의 ID
     * @param pageData 디스크에 쓸 ByteArray (페이지 크기와 동일)
     */
    fun writePage(pageId: Long, pageData: ByteArray){
        val offset = pageId * pageSize

        val buffer = ByteBuffer.wrap(pageData)
        fileChannel.write(buffer, offset)
    }

    /**
     * 파일 끝에 새로운 페이지를 위한 공간을 할당하고, 그 페이지의 ID를 반환합니다.
     * @return 새로 할당된 페이지의 ID
     */
    fun allocatePage(): Long{
        val newPageOffset = fileChannel.size()
        val newPageId = newPageOffset / pageSize

        return newPageId
    }

    /**
     * 현재까지 할당된 전체 페이지의 수를 반환합니다.
     */
    fun getNumPages(): Long = fileChannel.size() / pageSize

    fun close(){
        fileChannel.close()
    }
}
