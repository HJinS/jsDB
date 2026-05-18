package storage

import config.IndexConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import storageEngine.page.SlottedPage
import storageEngine.page.Page
import storageEngine.util.PageHeaderOffset
import java.lang.reflect.Field
import java.nio.ByteBuffer
import kotlin.random.Random


// get, update, insert, delete 테스트
// compaction 테스트 -> 압축 후에 free space 사이즈가 늘어야함
class SlottedPageTest:BehaviorSpec({
    afterEach {
        page.checkInvariant()
    }
    given("a empty page"){
        page.initData()
        val dummyItems = mutableListOf<Pair<ByteArray, ByteArray>>()
        `when`("insert 3 key, value items"){
            repeat(3){
                val dummyKey = ByteArray(30).apply { Random.nextBytes(this) }
                val dummyValue = ByteArray(30).apply { Random.nextBytes(this) }
                dummyItems.add(dummyKey to dummyValue)
                page.insertData(it, dummyKey, dummyValue)
            }

            then("page should have 3 items"){
                page.recordCount shouldBe 3
            }
        }

        `when`("delete 1 key, value pair"){
            val deleteSlotId = 2
            val (deletedKey, deletedValue) = page.deleteData(deleteSlotId)
            then("getData(2) result should be $dummyItems[2]"){
                val (dummyKey, dummyValue) = dummyItems[2]
                deletedKey contentEquals dummyKey
                deletedValue contentEquals dummyValue
            }
            dummyItems.removeAt(deleteSlotId)
        }

        `when`("update the second key"){
            val dummyKey = dummyItems[1].first
            val dummyNewValue = ByteArray(40).apply { Random.nextBytes(this) }
            val slotId = page.updateData(1, dummyKey, dummyNewValue)
            then("slot Id should be same"){
                slotId shouldBe 1
            }
            then("the new data retrieved should be equal to new data"){
                val (key, value) = page.getData(slotId)
                key shouldBe dummyKey
                value shouldBe dummyNewValue
            }
            then("the record count should be 2"){
                page.recordCount shouldBe 2
            }
        }

        `when`("insert new data"){
            val dummyNewKey = ByteArray(40).apply { Random.nextBytes(this) }
            val dummyNewValue = ByteArray(40).apply { Random.nextBytes(this) }
            var slotId = page.binarySearch(dummyNewKey)
            slotId = if(slotId >= 0) slotId+1 else -(slotId + 1)
            page.insertData(slotId, dummyNewKey, dummyNewValue)
            then("the record count should be 3"){
                page.recordCount shouldBe 3
            }
            then("the data retrieved should be equal to origin data"){
                val (key, value) = page.getData(slotId)
                key shouldBe dummyNewKey
                value shouldBe dummyNewValue
            }
        }
        `when`("compaction has done"){
            val dataField: Field = Page::class.java.getDeclaredField("data")
            dataField.isAccessible = true
            val buffer = dataField.get(page) as ByteBuffer
            val freeSpaceEndBefore = buffer.getShort(PageHeaderOffset.FREE_SPACE_END.offset)

            page.compaction()
            val freeSpaceEndAfter = buffer.getShort(PageHeaderOffset.FREE_SPACE_END.offset)
            then("Before value should smaller then after value"){
                freeSpaceEndBefore shouldBeLessThan freeSpaceEndAfter
            }
        }
    }
}){
    companion object{
        val pageSize = IndexConfig.pageSize
        val data: ByteBuffer = ByteBuffer.allocate(pageSize)
        val page = SlottedPage(IndexConfig, 1, data)
    }
}
