package storageEngine

import config.StorageConfig
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class DiskManager(storageConfig: StorageConfig, private val pageSize: Int) {
    init {
        require(pageSize > 0){"page size must be greater than zero"}
    }

    private val fileChannel: FileChannel = RandomAccessFile(storageConfig.dbPath, "rw").channel

    /**
     * Read page from disk with given [pageId] and load the data.
     * First page id should be 1.
     * @param pageId ID being read from disk.
     * @param pageData ByteBuffer to save the page data. It should be the same size as [pageSize].
     */
    fun readPage(pageId: Long, pageData: ByteBuffer){
        val offset = (pageId - 1) * pageSize

        val result = fileChannel.read(pageData, offset)
        if(result == -1){
            throw IllegalArgumentException("No such page")
        }
    }

    /**
     * Write the data to the page(pageId).
     * @param pageId Page id to write to.
     * @param pageData Data to write. It should be the same size as [pageSize].
     */
    fun writePage(pageId: Long, pageData: ByteBuffer){
        val offset = (pageId - 1) * pageSize
        fileChannel.write(pageData, offset)
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
