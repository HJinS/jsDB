package storageEngine

import config.StorageConfig
import config.IndexConfig
import storageEngine.exception.DiskManagerException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class DiskManager(storageConfig: StorageConfig, indexConfig: IndexConfig) {
    private val fileChannel: FileChannel = RandomAccessFile(storageConfig.dbPath, "rw").channel

    private val pageSize = indexConfig.pageSize

    /**
     * Read page from disk with given [pageId] and load the data.
     * First page id should be 1. PageID of "0"'ll be used as system meta page.
     * @param pageId ID being read from disk.
     * @param pageData ByteBuffer to save the page data. It should be the same size as [pageSize].
     */
    fun readPage(pageId: Long, pageData: ByteBuffer){
        require(pageData.capacity() == pageSize){
            "Page data must be exactly $pageSize bytes, but got ${pageData.capacity()}"
        }
        pageData.clear()
        val offset = pageId * pageSize
        while(pageData.hasRemaining()){
            val result = fileChannel.read(pageData, offset + pageData.position())
            if(result == -1) throw DiskManagerException.InvalidReadOffsetException(pageId)
        }
        pageData.rewind()
    }

    /**
     * Write the data to the page(pageId).
     * @param pageId Page id to write to.
     * @param pageData Data to write. It should be the same size as [pageSize].
     */
    fun writePage(pageId: Long, pageData: ByteBuffer){
        require(pageData.capacity() == pageSize){
            "Page data must be exactly $pageSize bytes, but got ${pageData.capacity()}"
        }
        pageData.rewind()
        val offset = pageId * pageSize
        while(pageData.hasRemaining()){
            fileChannel.write(pageData, offset + pageData.position())
        }

    }

    /**
     * Return current last page ID
     * @return current last page id
     */
    fun getNumPages(): Long{
        val size = fileChannel.size()
        if(size % pageSize != 0L) throw DiskManagerException.FileCorruptedException(size, pageSize)
        return fileChannel.size() / pageSize
    }

    /**
     * Close the file channel.
     * */
    fun close(){
        fileChannel.close()
    }
}
