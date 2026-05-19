package storage

import config.IndexConfig
import helper.serializer.InstantSerializerHelper
import index.serializer.MultiColumnKeySerializer
import helper.serializer.RowDataSerializerHelper
import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlinx.serialization.Serializable
import storageEngine.page.SlottedPage
import storageEngine.page.Page
import storageEngine.util.PageHeaderOffset
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Arrays


// get, update, insert, delete 테스트
// compaction 테스트 -> 압축 후에 free space 사이즈가 늘어야함
class SlottedPageTest:BehaviorSpec({
    afterEach {
        page.checkInvariant()
        page.checkSorted()
    }
    given("a empty page"){
        page.initData()
        val dummyItems = mutableListOf<Pair<ByteArray, ByteArray>>()
        val keyGenerator = Arb.bind(
            Arb.int(),
            Arb.instant(),
            Arb.string(maxSize = 6)
        ){ id, epoch, name -> listOf(id, epoch, name) }
        val valueGenerator = Arb.bind(
            Arb.int(),
            Arb.instant(),
            Arb.string(maxSize = 6)
        ){ id, epoch, name -> SampleData(id, epoch, name) }
        `when`("insert 5 key, value items"){
            repeat(10){
                val key = keyGenerator.next()
                val value = valueGenerator.next()
                dummyItems.add(serialize(key, value, keySerializer, valueSerializer))
                page.insertTyped(key, value, keySerializer, valueSerializer)
            }

            then("page should have 5 items"){
                page.recordCount shouldBe 10
            }
        }
        val dummyItemsSorted = dummyItems.sortedWith { data1, data2 -> Arrays.compareUnsigned(data1.first, data2.first) }.toMutableList()

        `when`("delete 1 key, value pair"){
            val deleteSlotId = 2
            val (deletedKey, deletedValue) = page.deleteData(deleteSlotId)
            val (expectedDeleteKey, expectedDeleteValue) = dummyItemsSorted[2]
            then("delete result should be $expectedDeleteKey, $expectedDeleteValue"){
                deletedKey contentEquals expectedDeleteKey
                deletedValue contentEquals expectedDeleteValue
            }
            dummyItemsSorted.removeAt(deleteSlotId)
        }

        `when`("update the second key"){
            val dummyKey = dummyItemsSorted[1].first
            val dummyNewValue = valueGenerator.next()
            val dummyNewValueSerialized = serializeValue(dummyNewValue, valueSerializer)

            val slotId = page.updateValueTyped(1, dummyNewValue, valueSerializer)
            then("slot Id should be same"){
                slotId shouldBe 1
            }
            then("the new data retrieved should be equal to new data"){
                val (key, value) = page.getData(slotId)
                key shouldBe dummyKey
                value shouldBe dummyNewValueSerialized
            }
            then("the record count should be 2"){
                page.recordCount shouldBe 9
            }
        }

        `when`("delete 1 key, value pair"){
            val deleteSlotId = 1
            val (deletedKey, deletedValue) = page.deleteData(deleteSlotId)
            val (expectedDeleteKey, expectedDeleteValue) = dummyItemsSorted[2]
            then("delete result should be $expectedDeleteKey, $expectedDeleteValue"){
                deletedKey contentEquals expectedDeleteKey
                deletedValue contentEquals expectedDeleteValue
            }
            dummyItemsSorted.removeAt(deleteSlotId)
        }

        `when`("insert new data"){
            val dummyNewKey = keyGenerator.next()
            val dummyNewValue = valueGenerator.next()
            val (dummyKeySerialized, dummyValueSerialized) = serialize(dummyNewKey, dummyNewValue, keySerializer, valueSerializer)
            val slotId = page.insertTyped(dummyNewKey, dummyNewValue, keySerializer, valueSerializer)
            then("the record count should be 2"){
                page.recordCount shouldBe 9
            }
            then("the data retrieved should be equal to origin data"){
                val (key, value) = page.getData(slotId)
                key shouldBe dummyKeySerialized
                value shouldBe dummyValueSerialized
            }
            dummyItemsSorted.add(slotId, dummyKeySerialized to dummyValueSerialized)
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
        val keySchema = KeySchema(listOf(
            Column("id", ColumnType.INT, descending = false),
            Column("epoch", ColumnType.INSTANT, descending = false),
            Column("name", ColumnType.STRING, descending = false),
        ))

        @Serializable
        data class SampleData(
            val id: Int,
            @Serializable(with = InstantSerializerHelper::class)
            val epoch: Instant,
            val name: String,
        )

        val keySerializer = MultiColumnKeySerializer(keySchema)
        val valueSerializer = RowDataSerializerHelper(SampleData::class)
        val page = SlottedPage(IndexConfig, 1, data)
    }
}