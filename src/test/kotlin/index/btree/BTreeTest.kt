package index.btree

import config.SimpleConfig
import config.StorageConfig
import helper.serializer.LocalDateSerializerHelper
import helper.serializer.RowDataSerializerHelper
import index.serializer.KeySerializer
import index.serializer.MultiColumnKeySerializer
import index.serializer.ValueSerializer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import storageEngine.BufferPoolManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.MetaPageManager
import storageEngine.StorageManager
import storageEngine.lru.FrameNodePolicy
import util.INVALID_PAGE_ID

/**
 * Test-only wrapper pairing a byte-only [BTree] with the key/value serializers, so existing test
 * bodies can keep using domain-typed keys/values.
 */
class TypedBTree<T : Any>(
    val btree: BTree,
    val keySerializer: KeySerializer<List<Any?>>,
    private val valueSerializer: ValueSerializer<T>,
) {
    fun insert(key: List<Any?>, value: T) {
        btree.insert(keySerializer.serialize(key), valueSerializer.serialize(value))
    }

    fun delete(key: List<Any?>) {
        btree.delete(keySerializer.serialize(key))
    }

    fun update(key: List<Any?>, newKey: List<Any?>, newValue: T) {
        btree.update(
            keySerializer.serialize(key),
            keySerializer.serialize(newKey),
            valueSerializer.serialize(newValue),
        )
    }

    fun search(key: List<Any?>): T? =
        btree.search(keySerializer.serialize(key))?.let { valueSerializer.deserialize(it).first }

    fun traverse(): List<Pair<List<Any?>, T>> =
        btree.traverse().map { (k, v) ->
            keySerializer.deserialize(k) to valueSerializer.deserialize(v).first
        }

    fun printTree() {
        btree.printTree { bytes -> keySerializer.format(keySerializer.deserialize(bytes)) }
    }

    /**
     * Drains a [BTree.search] scan (the range-scan entry point) into a typed list, for tests that
     * only care about the sequence of entries a seek + [Cursor.step] walk produces — not [Table]'s
     * open/closed boundary construction, which lives above this layer entirely and is covered by
     * `TableTest` instead.
     */
    fun scan(
        key: ByteArray?,
        direction: ScanDirection,
        boundGiven: Boolean,
    ): List<Pair<List<Any?>, T>> {
        val cursor = btree.search(key, direction, boundGiven) ?: return emptyList()
        val result = mutableListOf<Pair<List<Any?>, T>>()
        cursor.use {
            while (true) {
                val (k, v) = it.step() ?: break
                result += keySerializer.deserialize(k) to valueSerializer.deserialize(v).first
            }
        }
        return result
    }
}

class BTreeTest :
    BehaviorSpec({
        timeout = 5 * 60 * 1000L // 5 minutes — deadlock / infinite loop guard

        afterSpec {
            diskManager.close()
            File(config.storageConfig.dbPath).delete()
        }

        given("A Tree with two ids") {
            @Serializable data class IDData(val id: Int, val longId: Long)

            val schema =
                IndexKeySchema(
                    listOf(
                        IndexColumn("count", ColumnType.INT, descending = false),
                        IndexColumn("largeCount", ColumnType.LONG, descending = false),
                    )
                )
            val btree = initData<IDData>(schema)

            val keys =
                listOf(
                    listOf<Number>(1, 10L),
                    listOf<Number>(5, 50L),
                    listOf<Number>(3, 4L),
                    listOf<Number>(4, 1032L),
                    listOf<Number>(2, 12342L),
                    listOf<Number>(210, 1234203L),
                    listOf<Number>(523, 123932L),
                    listOf<Number>(12, 12342322L),
                    listOf<Number>(235, 123123932L),
                    listOf<Number>(21, 1231342L),
                    listOf<Number>(325, 1232932L),
                    listOf<Number>(32, 1223342L),
                    listOf<Number>(4, 23276L),
                    listOf<Number>(1, 10L),
                    listOf<Number>(2, 12342L),
                    listOf<Number>(21, 1231342L),
                )
            for (key in keys) {
                val value =
                    IDData(
                        id = key[0] as Int,
                        longId = key[1] as Long,
                    )
                btree.insert(key, value)
            }

            var deleteKey = listOf<Number>(523, 123932L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(3, 4L),
                        IDData(4, 1032L),
                        IDData(4, 23276L),
                        IDData(5, 50L),
                        IDData(12, 12342322L),
                        IDData(21, 1231342L),
                        IDData(21, 1231342L),
                        IDData(32, 1223342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(32, 1223342L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(3, 4L),
                        IDData(4, 1032L),
                        IDData(4, 23276L),
                        IDData(5, 50L),
                        IDData(12, 12342322L),
                        IDData(21, 1231342L),
                        IDData(21, 1231342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(12, 12342322L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(3, 4L),
                        IDData(4, 1032L),
                        IDData(4, 23276L),
                        IDData(5, 50L),
                        IDData(21, 1231342L),
                        IDData(21, 1231342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(4, 1032L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(3, 4L),
                        IDData(4, 23276L),
                        IDData(5, 50L),
                        IDData(21, 1231342L),
                        IDData(21, 1231342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(4, 23276L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(3, 4L),
                        IDData(5, 50L),
                        IDData(21, 1231342L),
                        IDData(21, 1231342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(5, 50L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(3, 4L),
                        IDData(21, 1231342L),
                        IDData(21, 1231342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(21, 1231342L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(3, 4L),
                        IDData(21, 1231342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(3, 4L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(21, 1231342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(21, 1231342L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(210, 1234203L),
                        IDData(235, 123123932L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(235, 123123932L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(210, 1234203L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(210, 1234203L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(2, 12342L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf<Number>(2, 12342L)
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        IDData(1, 10L),
                        IDData(1, 10L),
                        IDData(2, 12342L),
                        IDData(325, 1232932L),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }
        }

        given("A Tree with string localDate schema") {
            @Serializable
            data class UserData(
                val name: String,
                @Serializable(with = LocalDateSerializerHelper::class) val birthDate: LocalDate,
            )

            val schema =
                IndexKeySchema(
                    listOf(
                        IndexColumn("name", ColumnType.STRING, descending = false),
                        IndexColumn("date", ColumnType.LOCAL_DATE, descending = false),
                    )
                )
            val btree = initData<UserData>(schema)

            val keys2 =
                listOf(
                    listOf("Ava", LocalDate.of(2025, 4, 30)),
                    listOf("Grace", LocalDate.of(2024, 3, 20)),
                    listOf("Ava", LocalDate.of(2019, 12, 25)),
                    listOf("Elijah", LocalDate.of(1997, 12, 25)),
                    listOf("ElijahKim", LocalDate.of(1997, 12, 25)),
                    listOf("Lucas", LocalDate.of(1697, 12, 25)),
                    listOf("Faith", LocalDate.of(2022, 1, 18)),
                    listOf("Grace", LocalDate.of(2020, 1, 30)),
                    listOf("soif", LocalDate.of(2020, 1, 30)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Daniel", LocalDate.of(2018, 4, 9)),
                    listOf("Chloe", LocalDate.of(2019, 12, 25)),
                    listOf("Chloed", LocalDate.of(2020, 12, 25)),
                )

            for (key in keys2) {
                val value =
                    UserData(
                        name = key[0] as String,
                        birthDate = key[1] as LocalDate,
                    )
                btree.insert(key, value)
            }

            var deleteKey = listOf("ElijahKim", LocalDate.of(1997, 12, 25))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Ava", LocalDate.of(2025, 4, 30)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Chloed", LocalDate.of(2020, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Elijah", LocalDate.of(1997, 12, 25)),
                        UserData("Faith", LocalDate.of(2022, 1, 18)),
                        UserData("Grace", LocalDate.of(2020, 1, 30)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                        UserData("soif", LocalDate.of(2020, 1, 30)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Elijah", LocalDate.of(1997, 12, 25))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Ava", LocalDate.of(2025, 4, 30)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Chloed", LocalDate.of(2020, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Faith", LocalDate.of(2022, 1, 18)),
                        UserData("Grace", LocalDate.of(2020, 1, 30)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                        UserData("soif", LocalDate.of(2020, 1, 30)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Chloed", LocalDate.of(2020, 12, 25))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Ava", LocalDate.of(2025, 4, 30)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Faith", LocalDate.of(2022, 1, 18)),
                        UserData("Grace", LocalDate.of(2020, 1, 30)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                        UserData("soif", LocalDate.of(2020, 1, 30)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Grace", LocalDate.of(2020, 1, 30))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Ava", LocalDate.of(2025, 4, 30)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Faith", LocalDate.of(2022, 1, 18)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                        UserData("soif", LocalDate.of(2020, 1, 30)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Ava", LocalDate.of(2025, 4, 30))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Faith", LocalDate.of(2022, 1, 18)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                        UserData("soif", LocalDate.of(2020, 1, 30)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("soif", LocalDate.of(2020, 1, 30))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Faith", LocalDate.of(2022, 1, 18)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Faith", LocalDate.of(2022, 1, 18))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Daniel", LocalDate.of(2018, 4, 9))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Daniel", LocalDate.of(2018, 4, 9)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Daniel", LocalDate.of(2018, 4, 9))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                        UserData("Lucas", LocalDate.of(1697, 12, 25)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }

            deleteKey = listOf("Lucas", LocalDate.of(1697, 12, 25))
            `when`("Delete key $deleteKey") {
                btree.delete(deleteKey)
                btree.printTree()
                val expectedResults =
                    listOf(
                        UserData("Ava", LocalDate.of(2019, 12, 25)),
                        UserData("Chloe", LocalDate.of(2019, 12, 25)),
                        UserData("Grace", LocalDate.of(2024, 3, 20)),
                    )
                then("Trace result should be $expectedResults") {
                    val allKeys = btree.traverse()
                    allKeys.toList().map { it.second } shouldBe expectedResults
                }
            }
        }

        given("An empty Tree") {
            @Serializable data class IDData(val id: Int, val longId: Long)

            val schema =
                IndexKeySchema(
                    listOf(
                        IndexColumn("id", ColumnType.INT, descending = false),
                        IndexColumn("longId", ColumnType.LONG, descending = false),
                    )
                )
            val btree = initData<IDData>(schema)
            val dummyData = mutableListOf<IDData>()
            val dummyInt = (-20000..20000).shuffled().iterator()
            val dummyLong = (-20000L..20000).shuffled().iterator()
            repeat(3000) {
                dummyData.add(IDData(dummyInt.next(), dummyLong.next()))
            }
            val expectedSorted = dummyData.sortedWith(compareBy({ it.id }, { it.longId }))
            val expectedMap: MutableMap<List<Number>, IDData> = mutableMapOf()
            var updateTargetData: IDData? = null
            var updateNewValue: IDData? = null

            `when`("inserting all 3000 records in shuffled order") {
                for (data in dummyData) {
                    val key = listOf<Number>(data.id, data.longId)
                    btree.insert(key, data)
                    expectedMap[key] = data
                }
                then("traverse returns all 3000 records in sorted order") {
                    val result = btree.traverse().map { it.second }
                    result.size shouldBe 3000
                    result shouldBe expectedSorted
                }

                then("search finds the correct value for each inserted key") {
                    for (data in dummyData) {
                        btree.search(listOf<Number>(data.id, data.longId)) shouldBe data
                    }
                }
            }
            `when`("update one record") {
                val newInt = dummyInt.next()
                val newLong = dummyLong.next()
                val newValue = IDData(newInt, newLong)
                val targetData = dummyData[100]
                updateTargetData = targetData
                updateNewValue = newValue
                val targetKey = listOf<Number>(targetData.id, targetData.longId)
                expectedMap[targetKey] = newValue
                then("then the value should be updated") {
                    btree.update(targetKey, targetKey, newValue)
                    val searchResult = btree.search(targetKey)
                    searchResult shouldBe newValue
                }
                then("traverse key order still correct after update") {
                    val result = btree.traverse()
                    result.size shouldBe 3000
                    val keys = result.map { it.first }
                    val sortedKeys = keys.sortedWith(compareBy({ it[0] as Int }, { it[1] as Long }))
                    keys shouldBe sortedKeys
                }
            }

            `when`("update one record which doesn't exist in the btree") {
                val newInt = dummyInt.next()
                val newLong = dummyLong.next()
                val newValue = IDData(newInt, newLong)
                val targetKey = listOf<Number>(newValue.id, newValue.longId)
                then("then given key should not be searched") {
                    btree.update(targetKey, targetKey, newValue)
                    val searchResult = btree.search(targetKey)
                    searchResult shouldBe null
                }
            }
            `when`("delete all data") {
                val expected = dummyData.sortedWith(compareBy({ it.id }, { it.longId }))
                val expectedMutable = expected.toMutableList()
                if (updateTargetData != null && updateNewValue != null) {
                    val ui = expectedMutable.indexOf(updateTargetData)
                    if (ui >= 0) expectedMutable[ui] = updateNewValue
                }
                var initialSize = 3000
                for ((idx, data) in dummyData.withIndex()) {
                    val deleteKey = listOf<Number>(data.id, data.longId)
                    initialSize -= 1
                    expectedMap.remove(deleteKey)
                    if (data == updateTargetData && updateNewValue != null) {
                        expectedMutable.remove(updateNewValue)
                    } else {
                        expectedMutable.remove(data)
                    }
                    btree.delete(deleteKey)
                    then("loop $idx sorted order preserved after deletion") {
                        val traversed = btree.traverse().map { it.second }
                        traversed.size shouldBe initialSize
                        traversed shouldBe expectedMutable
                    }
                    then("loop $idx: deleted key returns null on search") {
                        btree.search(deleteKey) shouldBe null
                    }
                    if (expectedMap.size >= 10) {
                        then("loop $idx: remaining keys are still searchable") {
                            expectedMap.entries.shuffled().take(10).forEach { (key, expectedValue)
                                ->
                                btree.search(key) shouldBe expectedValue
                            }
                        }
                    }
                }
            }
        }

        given("A Tree for testing update(key, newKey, newValue) with a changed key") {
            @Serializable data class IDData(val id: Int, val longId: Long)

            val schema =
                IndexKeySchema(
                    listOf(
                        IndexColumn("id", ColumnType.INT, descending = false),
                        IndexColumn("longId", ColumnType.LONG, descending = false),
                    )
                )
            val btree = initData<IDData>(schema)

            val recordCount = 300
            for (i in 0 until recordCount) {
                val key = listOf<Number>(i, 0L)
                btree.insert(key, IDData(i, 0L))
            }

            `when`("updating a key to a value that still sorts near it (likely same leaf)") {
                val oldKey = listOf<Number>(150, 0L)
                val newKey = listOf<Number>(150, 1L)
                val newValue = IDData(150, 1L)
                btree.update(oldKey, newKey, newValue)
                then("old key is gone and new key returns the new value") {
                    btree.search(oldKey) shouldBe null
                    btree.search(newKey) shouldBe newValue
                }
                then("traverse still returns every record in sorted key order") {
                    val keys = btree.traverse().map { it.first }
                    val sortedKeys = keys.sortedWith(compareBy({ it[0] as Int }, { it[1] as Long }))
                    keys shouldBe sortedKeys
                    keys.size shouldBe recordCount
                }
            }

            `when`("updating a key to a value that must move to a different leaf") {
                val oldKey = listOf<Number>(0, 0L)
                val newKey = listOf<Number>(recordCount + 500, 0L)
                val newValue = IDData(recordCount + 500, 0L)
                btree.update(oldKey, newKey, newValue)
                then("old key is gone and new key returns the new value") {
                    btree.search(oldKey) shouldBe null
                    btree.search(newKey) shouldBe newValue
                }
                then("traverse still returns every record exactly once, in sorted key order") {
                    val keys = btree.traverse().map { it.first }
                    val sortedKeys = keys.sortedWith(compareBy({ it[0] as Int }, { it[1] as Long }))
                    keys shouldBe sortedKeys
                    keys.size shouldBe recordCount
                }
            }

            `when`("updating that is currently the smallest key(position 0 of the leftmost leaf)") {
                // 앞 시나리오에서 (0,0)이 이미 빠졌으니, 지금 가장 작은 키는 (1,0).
                val currentSmallest = listOf<Number>(1, 0L)
                val newKey = listOf<Number>(-5, 0L) // 여전히 새 최솟값이 되도록 — separator 전파를 검증
                val newValue = IDData(-5, 0L)
                btree.update(currentSmallest, newKey, newValue)
                then("old key is gone and new key is searchable") {
                    btree.search(currentSmallest) shouldBe null
                    btree.search(newKey) shouldBe newValue
                }
                then(
                    "every other previously-inserted key is still searchable(ancestor separator wasn't corrupted)"
                ) {
                    for (i in 2 until 20) {
                        btree.search(listOf<Number>(i, 0L)) shouldBe IDData(i, 0L)
                    }
                }
                then("traverse order is still fully sorted") {
                    val keys = btree.traverse().map { it.first }
                    val sortedKeys = keys.sortedWith(compareBy({ it[0] as Int }, { it[1] as Long }))
                    keys shouldBe sortedKeys
                }
            }

            `when`("updating a non-existent key") {
                val missingKey = listOf<Number>(999_999, 0L)
                val newKey = listOf<Number>(999_998, 0L)
                btree.update(missingKey, newKey, IDData(999_998, 0L))
                then("nothing changes, and the tree is still fully usable right after") {
                    btree.search(newKey) shouldBe null
                    // update()의 no-op(!isExist) 경로에서 leaf의 write lock이 안 풀렸다면,
                    // 바로 이어지는 아래 호출이 그 leaf를 다시 잡으려다 걸릴 수 있다.
                    val probeKey = listOf<Number>(250, 0L)
                    btree.update(probeKey, probeKey, IDData(250, 999L))
                    btree.search(probeKey) shouldBe IDData(250, 999L)
                }
            }
        }

        given("A Tree used for range-scan (BTree.search) coverage") {
            @Serializable data class ScanData(val id: Long)

            val schema =
                IndexKeySchema(listOf(IndexColumn("id", ColumnType.LONG, descending = false)))
            val btree = initData<ScanData>(schema)

            val ids = (1L..20L).toList()
            for (id in ids) {
                btree.insert(listOf(id), ScanData(id))
            }

            `when`("scanning FORWARD from an explicit lower bound") {
                then("every entry from that key to the tree's right edge comes back in order") {
                    val key = btree.keySerializer.serialize(listOf(5L))
                    val result = btree.scan(key, ScanDirection.FORWARD, boundGiven = true)
                    result.map { it.second.id } shouldBe (5L..20L).toList()
                }
            }

            `when`("scanning BACKWARD from an explicit upper bound") {
                then(
                    "every entry from that key down to the tree's left edge comes back in reverse order"
                ) {
                    // BACKWARD seek is inclusive via serializeUpper (see serializeBound's table) —
                    // plain serialize(15) would seek to just *before* 15, excluding it.
                    val key = btree.keySerializer.serializeUpper(listOf(15L))
                    val result = btree.scan(key, ScanDirection.BACKWARD, boundGiven = true)
                    result.map { it.second.id } shouldBe (15L downTo 1L).toList()
                }
            }

            `when`("scanning FORWARD with no bound at all") {
                then("the whole tree comes back ascending, via the leftmost-leaf descent") {
                    val result = btree.scan(null, ScanDirection.FORWARD, boundGiven = false)
                    result.map { it.second.id } shouldBe ids
                }
            }

            `when`("scanning BACKWARD with no bound at all") {
                then("the whole tree comes back descending, via the rightmost-leaf descent") {
                    val result = btree.scan(null, ScanDirection.BACKWARD, boundGiven = false)
                    result.map { it.second.id } shouldBe ids.reversed()
                }
            }

            `when`("the tree has already been fully scanned and the cursor closed once") {
                then("it's still usable for a plain insert — no lock was left behind by the scan") {
                    btree.scan(null, ScanDirection.FORWARD, boundGiven = false)
                    btree.insert(listOf(21L), ScanData(21L))
                    btree.search(listOf(21L)) shouldBe ScanData(21L)
                }
            }
        }

        given("An empty Tree used for range-scan coverage") {
            @Serializable data class ScanData(val id: Long)

            val schema =
                IndexKeySchema(listOf(IndexColumn("id", ColumnType.LONG, descending = false)))
            val btree = initData<ScanData>(schema)

            `when`("scanning an empty tree in either direction") {
                then("no results come back and nothing throws") {
                    btree.scan(null, ScanDirection.FORWARD, boundGiven = false) shouldBe emptyList()
                    btree.scan(null, ScanDirection.BACKWARD, boundGiven = false) shouldBe
                        emptyList()
                }
            }
        }

        given("A large Tree spanning multiple leaf pages") {
            @Serializable data class ScanData(val id: Long)

            val schema =
                IndexKeySchema(listOf(IndexColumn("id", ColumnType.LONG, descending = false)))
            val btree = initData<ScanData>(schema)

            val recordCount = 800
            for (id in 0 until recordCount) {
                btree.insert(listOf(id.toLong()), ScanData(id.toLong()))
            }

            `when`("scanning FORWARD from the middle, crossing several leaf boundaries") {
                then(
                    "every entry from that point to the end comes back in order, none skipped or duplicated"
                ) {
                    val key = btree.keySerializer.serialize(listOf(300L))
                    val result = btree.scan(key, ScanDirection.FORWARD, boundGiven = true)
                    result.map { it.second.id } shouldBe (300L until recordCount.toLong()).toList()
                }
            }

            `when`("scanning BACKWARD from the middle, crossing several leaf boundaries") {
                then("every entry from that point back to the start comes back in reverse order") {
                    val key = btree.keySerializer.serializeUpper(listOf(500L))
                    val result = btree.scan(key, ScanDirection.BACKWARD, boundGiven = true)
                    result.map { it.second.id } shouldBe (500L downTo 0L).toList()
                }
            }
        }

        given("A Tree with a single descending column that can hold NULL") {
            @Serializable data class ScanData(val priority: Long?)

            val schema =
                IndexKeySchema(listOf(IndexColumn("priority", ColumnType.LONG, descending = true)))
            val btree = initData<ScanData>(schema)

            btree.insert(listOf(30L), ScanData(30L))
            btree.insert(listOf(10L), ScanData(10L))
            btree.insert(listOf(null), ScanData(null))

            `when`(
                "computing serializeUpper for a value that is exactly NULL on this DESC column"
            ) {
                then(
                    "it returns null — that NULL group already sits at the tree's physical right edge"
                ) {
                    btree.keySerializer.serializeUpper(listOf(null)) shouldBe null
                }
            }

            `when`("seeking BACKWARD with that null successor") {
                then(
                    "it falls back to the tree's rightmost leaf, landing on the NULL entry itself"
                ) {
                    val result = btree.scan(null, ScanDirection.BACKWARD, boundGiven = true)
                    result.first().second.priority shouldBe null
                }
            }

            `when`("seeking FORWARD with that null successor") {
                then("nothing sorts past the tree's own right edge, so the scan is empty") {
                    val result = btree.scan(null, ScanDirection.FORWARD, boundGiven = true)
                    result shouldBe emptyList()
                }
            }
        }
    }) {
    companion object {
        val config = SimpleConfig(StorageConfig(dbPath = "test-btree.db", poolSize = 100))
        val diskManager = DiskManager(config.storageConfig, config.indexConfig)
        val lruPolicy = FrameNodePolicy(config.storageConfig.midPointLruConfig)
        val bufferPoolManager =
            BufferPoolManager(
                diskManager,
                lruPolicy,
                config.indexConfig,
                config.storageConfig.poolSize,
            )
        val metaPageManager = MetaPageManager(bufferPoolManager)
        val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
        val storageManager = StorageManager(freeSpaceManager, bufferPoolManager, config.indexConfig)
        var metaInitialized = false

        inline fun <reified T : Any> initData(schema: IndexKeySchema): TypedBTree<T> {
            if (!metaInitialized) {
                metaPageManager.initialize()
                metaInitialized = true
            }
            val btree =
                BTree(
                    "test",
                    "test table",
                    storageManager,
                    config.indexConfig,
                    INVALID_PAGE_ID,
                )
            return TypedBTree(
                btree,
                MultiColumnKeySerializer(schema),
                RowDataSerializerHelper(serializer<T>()),
            )
        }
    }
}
