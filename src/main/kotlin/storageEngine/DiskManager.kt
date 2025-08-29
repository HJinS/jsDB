package storageEngine

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class DiskManager(dbPath: String, private val pageSize: Int) {
    init {
        require(pageSize > 0){"page size must be greater than zero"}
    }

    private val fileChannel: FileChannel = RandomAccessFile(dbPath, "rw").channel

    /**
     * Read page from disk with given [pageId] and load the data.
     * First page id should be 1.
     * @param pageId ID being read from disk.
     * @param pageData ByteArray to save the page data. It should be the same size as [pageSize].
     */
    fun readPage(pageId: Long, pageData: ByteArray){
        val offset = (pageId - 1) * pageSize

        val buffer = ByteBuffer.wrap(pageData)
        val result = fileChannel.read(buffer, offset)
        if(result == -1){
            throw IllegalArgumentException("No such page")
        }
    }

    /**
     * Write the data to the page(pageId).
     * @param pageId Page id to write to.
     * @param pageData Data to write. It should be the same size as [pageSize].
     */
    fun writePage(pageId: Long, pageData: ByteArray){
        val offset = (pageId - 1) * pageSize

        val buffer = ByteBuffer.wrap(pageData)
        fileChannel.write(buffer, offset)
    }

    /**
     * Return new pageId.
     * The file will be managed automatically by FileChannel.
     * @return New page id.
     */
    fun allocatePage(): Long = fileChannel.size() / pageSize + 1

    /**
     * Return current last page ID
     * @return current last page id
     */
    fun getNumPages(): Long = fileChannel.size() / pageSize

    /**
     * Close the file channel.
     * */
    fun close(){
        fileChannel.close()
    }
}
