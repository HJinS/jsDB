package storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import storageEngine.Page
import storageEngine.util.PageHeaderOffset
import java.lang.reflect.Field
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.reflect.full.memberProperties


// get, update, insert, delete 테스트
// compaction 테스트 -> 압축 후에 free space 사이즈가 늘어야함
class PageTest:BehaviorSpec({

    given("a empty page"){
        `when`("insert 3 key, value items"){
            val dummyItems = mutableListOf<Pair<ByteArray, ByteArray>>()
            repeat(3){
                val dummyKey = ByteArray(30).apply { Random.nextBytes(this) }
                val dummyValue = ByteArray(30).apply { Random.nextBytes(this) }
                page.insertData(dummyKey, dummyValue)
            }

            then("page should have 3 items"){
                page.recordCount shouldBe 3
            }
        }

        `when`("delete 1 key, value pair"){
            val deleteSlotId = 2
            page.deleteData(deleteSlotId)
            then("getData(2) should throw an exception"){
                shouldThrow<IllegalStateException> { page.getData(deleteSlotId) }
            }
        }

        `when`("update the second data"){
            val dummyNewKey = ByteArray(40).apply { Random.nextBytes(this) }
            val dummyNewValue = ByteArray(40).apply { Random.nextBytes(this) }
            val slotId = page.updateData(1, dummyNewKey, dummyNewValue)
            then("the first data should have new data and should use used slot 2"){
                slotId shouldBe 2
            }
            then("the new data retrieved should be equal to new data"){
                val (key, value) = page.getData(slotId)
                key shouldBe dummyNewKey
                value shouldBe dummyNewValue
            }
            then("the record count should be 2"){
                page.recordCount shouldBe 2
            }
        }

        `when`("insert new data"){
            val dummyNewKey = ByteArray(40).apply { Random.nextBytes(this) }
            val dummyNewValue = ByteArray(40).apply { Random.nextBytes(this) }
            val slotId = page.insertData(dummyNewKey, dummyNewValue)
            then("the new data should have slotId 1"){
                slotId shouldBe 1
            }
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
            val bufferDelegateField: Field = Page::class.java.getDeclaredField("buffer\$delegate")
            bufferDelegateField.isAccessible = true
            val bufferDelegate = bufferDelegateField.get(page) as Lazy<*>
            val buffer = bufferDelegate.value as ByteBuffer
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
        val page = Page(0)
    }
}