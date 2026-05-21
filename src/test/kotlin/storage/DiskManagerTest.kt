package storage

import config.StorageConfig
import config.IndexConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import storageEngine.DiskManager
import storageEngine.exception.DiskManagerException
import java.io.File
import java.nio.ByteBuffer


class DiskManagerTest: BehaviorSpec({
    afterSpec {
        diskManager.close()
        val file = File(DBPATH)
        file.delete()
    }

    given("disk manager with empty file"){
        `when`("read page with buffer size PAGE_SIZE/2"){
            val buffer = ByteBuffer.allocate(PAGE_SIZE/2)
            then("should throw an InvalidReadOffsetException"){
                val error = shouldThrow<IllegalArgumentException> { diskManager.readPage(0, buffer) }
                error.message shouldBe "Page data must be exactly $PAGE_SIZE bytes, but got ${buffer.capacity()}"
            }
        }

        `when`("read page from an empty disk"){
            val buffer = ByteBuffer.allocate(PAGE_SIZE)
            then("should throw an InvalidReadOffsetException"){
                shouldThrow<DiskManagerException.InvalidReadOffsetException> { diskManager.readPage(0, buffer) }
            }
        }

        `when`("write page with pageId zero"){
            val data = 1239238
            val buffer = ByteBuffer.allocate(PAGE_SIZE).putInt(data)
            diskManager.writePage(0L, buffer)
            then("current total page should be 1"){
                val totalPage = diskManager.getNumPages()
                totalPage shouldBe 1
            }
        }

        `when`("read page 1 from empty database file"){
            val buffer = ByteBuffer.allocate(PAGE_SIZE)
            then("should throw an InvalidReadOffsetException"){
                shouldThrow<DiskManagerException.InvalidReadOffsetException> { diskManager.readPage(1, buffer) }
            }
        }
        `when`("write data"){
            val data = 1239238
            val buffer = ByteBuffer.allocate(PAGE_SIZE).putInt(data)
            diskManager.writePage(1, buffer)
            then("current total page should be 2"){
                diskManager.getNumPages() shouldBe 2
            }

            val byteBufferForRead = ByteBuffer.allocate(PAGE_SIZE)
            diskManager.readPage(1, byteBufferForRead)
            then("read result should be same"){
                byteBufferForRead.getInt() shouldBe data
            }
        }
    }
}){
    companion object {
        private val DBPATH = StorageConfig.dbPath
        private val PAGE_SIZE = IndexConfig.pageSize
        private val diskManager = DiskManager(StorageConfig, IndexConfig)
    }
}
