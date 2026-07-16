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
                page.recordCount shouldBe 8
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
                page.recordCount shouldBe 8
            }
            then("the data retrieved should be equal to origin data"){
                val (key, value) = page.getData(slotId)
                key shouldBe dummyKeySerialized
                value shouldBe dummyValueSerialized
            }
            dummyItemsSorted.add(slotId, dummyKeySerialized to dummyValueSerialized)
        }
    }

    given("a page with records inserted in descending order causing repeated right slot shifts") {
        val freshBuffer = ByteBuffer.allocate(indexConfig.pageSize)
        val freshPage = SlottedPage(indexConfig, 99L, freshBuffer)
        freshPage.initData()
        val insertedKeys = mutableListOf<ByteArray>()

        `when`("inserting 5 records in descending key order so each insert shifts all existing slots right") {
            for (k in 5 downTo 1) {
                val (kBytes, vBytes) = serialize(
                    listOf(k, Instant.EPOCH, ""),
                    SampleData(k, Instant.EPOCH, ""),
                    keySerializer,
                    valueSerializer
                )
                val slotId = freshPage.getInsertPosition(kBytes)
                freshPage.insertData(slotId, kBytes, vBytes)
                insertedKeys.add(0, kBytes)
            }

            then("record count should be 5") {
                freshPage.recordCount shouldBe 5
            }

            then("all records should return correct key data after right shifts") {
                for (i in 0 until freshPage.recordCount) {
                    val (key, _) = freshPage.getData(i)
                    key shouldBe insertedKeys[i]
                }
            }

            then("records should be in sorted order") {
                freshPage.checkSorted()
            }

            then("page invariant should hold") {
                freshPage.checkInvariant()
            }
        }
    }

    given("a page with a record inserted in the middle of existing records") {
        val middleBuffer = ByteBuffer.allocate(indexConfig.pageSize)
        val middlePage = SlottedPage(indexConfig, 100L, middleBuffer)
        middlePage.initData()
        val expectedKeys = mutableListOf<ByteArray>()

        `when`("inserting 5 odd keys then inserting an even key in the middle") {
            for (k in listOf(1, 3, 5, 7, 9)) {
                val (kBytes, vBytes) = serialize(
                    listOf(k, Instant.EPOCH, ""),
                    SampleData(k, Instant.EPOCH, ""),
                    keySerializer,
                    valueSerializer
                )
                middlePage.insertData(middlePage.getInsertPosition(kBytes), kBytes, vBytes)
                expectedKeys.add(kBytes)
            }
            val (midKey, midVal) = serialize(
                listOf(4, Instant.EPOCH, ""),
                SampleData(4, Instant.EPOCH, ""),
                keySerializer,
                valueSerializer
            )
            val insertPos = middlePage.getInsertPosition(midKey)
            middlePage.insertData(insertPos, midKey, midVal)
            expectedKeys.add(insertPos, midKey)

            then("record count should be 6") {
                middlePage.recordCount shouldBe 6
            }

            then("all records after insertion point should not be corrupted") {
                for (i in 0 until middlePage.recordCount) {
                    val (key, _) = middlePage.getData(i)
                    key shouldBe expectedKeys[i]
                }
            }

            then("records should remain sorted after middle insertion") {
                middlePage.checkSorted()
            }
        }
    }

}){
    companion object{
        val indexConfig = IndexConfig()
        val pageSize = indexConfig.pageSize
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
        val valueSerializer = RowDataSerializerHelper(SampleData.serializer())
        val page = SlottedPage(indexConfig, 1, data)
    }
}