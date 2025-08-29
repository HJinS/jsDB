package storage

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

    given("a non-initialized DiskManager class"){
        `when`("initialize DiskManager with PAGE_SIZE=0"){
            then("should throw an IllegalArgumentException"){
                val error = shouldThrow<IllegalArgumentException> { DiskManager(DBPATH, 0) }
                error.message shouldBe "page size must be greater than zero"
            }
            File(DBPATH).delete()
        }
    }

    given("disk manager with empty file"){
        `when`("read page with pageId zero"){
            val byteArray = ByteArray(PAGE_SIZE)
            then("should throw an IllegalArgumentException"){
                val error = shouldThrow<IllegalArgumentException> { diskManager.readPage(0,byteArray) }
                error.message shouldBe "Negative position"
            }
        }

        `when`("write page with pageId zero"){
            val data = 1239238
            val byteArray = ByteBuffer.allocate(PAGE_SIZE).putInt(data).array()
            then("should throw an IllegalArgumentException"){
                val error = shouldThrow<IllegalArgumentException> { diskManager.writePage(0,byteArray) }
                error.message shouldBe "Negative position"
            }
        }
        `when`("allocate page"){
            val newPageId = diskManager.allocatePage()
            then("should return a new page id 1"){
                newPageId shouldBe 1L
            }
        }
        `when`("read page 1 from empty database file"){
            val byteArray = ByteArray(PAGE_SIZE)
            then("should throw an IllegalArgumentException"){
                val error = shouldThrow<IllegalArgumentException> { diskManager.readPage(2,byteArray) }
                error.message shouldBe "No such page"
            }
        }
        `when`("write data"){
            val data = 1239238
            val byteArray = ByteBuffer.allocate(PAGE_SIZE).putInt(data).array()
            diskManager.writePage(1, byteArray)
            then("write action ends successfully."){
                diskManager.getNumPages() shouldBe 1
            }

            val byteArrayForRead = ByteArray(PAGE_SIZE)
            diskManager.readPage(1,byteArrayForRead)
            then("read result should be same"){
                ByteBuffer.wrap(byteArrayForRead).int shouldBe data
            }
        }
        `when`("allocate new page"){
            val newPageId = diskManager.allocatePage()
            then("page id should be 2 because of already written page"){
                newPageId shouldBe 2L
            }
        }
    }
}){
    companion object {
        private const val DBPATH = "./test.db"
        private const val PAGE_SIZE = 2048
        private val diskManager = DiskManager(DBPATH, PAGE_SIZE)
    }
}