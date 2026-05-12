package storage

import config.StorageConfig
import config.IndexConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import storageEngine.DiskManager
import java.io.File
import java.nio.ByteBuffer


class DiskManagerTest: BehaviorSpec({
    afterSpec {
        diskManager.close()
        val file = File(DBPATH)
        file.delete()
    }

    given("disk manager with empty file"){
        `when`("read page with pageId zero"){
            val buffer = ByteBuffer.allocate(PAGE_SIZE)
            then("should throw an IllegalArgumentException"){
                val error = shouldThrow<IllegalArgumentException> { diskManager.readPage(0, buffer) }
                error.message shouldBe "Negative position"
            }
        }

        `when`("write page with pageId zero"){
            val data = 1239238
            val buffer = ByteBuffer.allocate(PAGE_SIZE).putInt(data)
            then("should throw an IllegalArgumentException"){
                val error = shouldThrow<IllegalArgumentException> { diskManager.writePage(0, buffer) }
                error.message shouldBe "Negative position"
            }
        }
        `when`("read page 1 from empty database file"){
            val buffer = ByteBuffer.allocate(PAGE_SIZE)
            then("should throw an IllegalArgumentException"){
                val error = shouldThrow<IllegalArgumentException> { diskManager.readPage(2, buffer) }
                error.message shouldBe "No such page"
            }
        }
        `when`("write data"){
            val data = 1239238
            val buffer = ByteBuffer.allocate(PAGE_SIZE).putInt(data)
            diskManager.writePage(1, buffer)
            then("write action ends successfully."){
                diskManager.getNumPages() shouldBe 1
            }

            val byteBufferForRead = ByteBuffer.allocate(PAGE_SIZE)
            diskManager.readPage(1, byteBufferForRead)
            then("read result should be same"){
                byteBufferForRead.getInt(0) shouldBe data
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
