package index.btree

import config.IndexConfig
import config.MidpointLruConfig
import config.StorageConfig
import helper.serializer.LocalDateSerializerHelper
import helper.serializer.RowDataSerializerHelper
import index.serializer.MultiColumnKeySerializer
import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import storageEngine.BufferPoolManager
import storageEngine.DatabaseInitializer
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.StorageManager
import storageEngine.lru.MidpointLRUPolicy
import java.time.LocalDate

class BTreeTest: BehaviorSpec({
    given("A Tree with two ids"){
        @Serializable
        data class IDData(val id: Int, val longId: Long)

        val schema = KeySchema(listOf(
            Column("count", ColumnType.INT, descending = false),
            Column("largeCount", ColumnType.LONG, descending = false)
        ))
        val btree = initData<IDData>(schema)

        val keys = listOf(
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
            listOf<Number>(21, 1231342L)
        )
        for (key in keys) {
            val value = IDData(
                id = key[0] as Int,
                longId = key[1] as Long
            )
            btree.insert(key, value)
        }

        var deleteKey = listOf<Number>(523, 123932L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(3,4L),
                IDData(4,1032L),
                IDData(4,23276L),
                IDData(5,50L),
                IDData(12,12342322L),
                IDData(21,1231342L),
                IDData(21,1231342L),
                IDData(32,1223342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(32,1223342L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(3,4L),
                IDData(4,1032L),
                IDData(4,23276L),
                IDData(5,50L),
                IDData(12,12342322L),
                IDData(21,1231342L),
                IDData(21,1231342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(12, 12342322L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(3,4L),
                IDData(4,1032L),
                IDData(4,23276L),
                IDData(5,50L),
                IDData(21,1231342L),
                IDData(21,1231342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(4,1032L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(3,4L),
                IDData(4,23276L),
                IDData(5,50L),
                IDData(21,1231342L),
                IDData(21,1231342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(4,23276L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(3,4L),
                IDData(5,50L),
                IDData(21,1231342L),
                IDData(21,1231342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(5,50L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(3,4L),
                IDData(21,1231342L),
                IDData(21,1231342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(21,1231342L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(3,4L),
                IDData(21,1231342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(3,4L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(21,1231342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(21,1231342L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(210,1234203L),
                IDData(235,123123932L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(235,123123932L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(210,1234203L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(210,1234203L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(2,12342L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf<Number>(2,12342L)
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                IDData(1,10L),
                IDData(1,10L),
                IDData(2,12342L),
                IDData(325,1232932L)
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }
    }

    given("A Tree with string localDate schema"){
        @Serializable
        data class UserData(
            val name: String,
            @Serializable(with = LocalDateSerializerHelper::class)
            val birthDate: LocalDate
        )

        val schema = KeySchema(listOf(
            Column("name", ColumnType.STRING, descending = false),
            Column("date", ColumnType.LOCAL_DATE, descending = false)
        ))
        val btree = initData<UserData>(schema)

        val keys2 = listOf(
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
            listOf("Chloed", LocalDate.of(2020, 12, 25))
        )

        for (key in keys2) {
            val value = UserData(
                name = key[0] as String,
                birthDate = key[1] as LocalDate,
            )
            btree.insert(key, value)
        }

        var deleteKey = listOf("ElijahKim", LocalDate.of(1997, 12, 25))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
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
                UserData("soif", LocalDate.of(2020, 1, 30))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Elijah", LocalDate.of(1997, 12, 25))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
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
                UserData("soif", LocalDate.of(2020, 1, 30))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Chloed", LocalDate.of(2020, 12, 25))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Ava", LocalDate.of(2025, 4, 30)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Faith", LocalDate.of(2022, 1, 18)),
                UserData("Grace", LocalDate.of(2020, 1, 30)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Lucas", LocalDate.of(1697, 12, 25)),
                UserData("soif", LocalDate.of(2020, 1, 30))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Grace", LocalDate.of(2020, 1, 30))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Ava", LocalDate.of(2025, 4, 30)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Faith", LocalDate.of(2022, 1, 18)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Lucas", LocalDate.of(1697, 12, 25)),
                UserData("soif", LocalDate.of(2020, 1, 30))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Ava", LocalDate.of(2025, 4, 30))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Faith", LocalDate.of(2022, 1, 18)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Lucas", LocalDate.of(1697, 12, 25)),
                UserData("soif", LocalDate.of(2020, 1, 30))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("soif", LocalDate.of(2020, 1, 30))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Faith", LocalDate.of(2022, 1, 18)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Lucas", LocalDate.of(1697, 12, 25))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Faith", LocalDate.of(2022, 1, 18))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Lucas", LocalDate.of(1697, 12, 25))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Daniel", LocalDate.of(2018, 4, 9))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Lucas", LocalDate.of(1697, 12, 25))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Daniel", LocalDate.of(2018, 4, 9))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Lucas", LocalDate.of(1697, 12, 25))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }

        deleteKey = listOf("Lucas", LocalDate.of(1697, 12, 25))
        `when`("Delete key $deleteKey"){
            btree.delete(deleteKey)
            btree.printTree()
            val expectedResults = listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Grace", LocalDate.of(2024, 3, 20))
            )
            then("Trace result should be $expectedResults"){
                val allKeys = btree.traverse()
                allKeys.toList().map{ it.second } shouldBe expectedResults
            }
        }
    }

    given("A Tree with 500 shuffled composite keys inserted"){
        @Serializable
        data class IDData(val id: Int, val longId: Long)

        val schema = KeySchema(listOf(
            Column("id", ColumnType.INT, descending = false),
            Column("longId", ColumnType.LONG, descending = false)
        ))
        val btree = initData<IDData>(schema)
        val dummyData = mutableListOf<IDData>()
        val dummyInt = (-20000..20000).shuffled().iterator()
        val dummyLong = (-20000L..20000).shuffled().iterator()
        repeat(500){
            dummyData.add(IDData(dummyInt.next(), dummyLong.next()))
        }
        val expectedSorted = dummyData.sortedWith(compareBy({ it.id }, { it.longId }))

        `when`("inserting all 500 records in shuffled order") {
            for (data in dummyData) {
                btree.insert(listOf<Number>(data.id, data.longId), data)
            }

            then("traverse returns all 500 records in sorted order") {
                val result = btree.traverse().map { it.second }
                result.size shouldBe 500
                result shouldBe expectedSorted
            }

            then("search finds the correct value for each inserted key") {
                for (data in dummyData) {
                    btree.search(listOf<Number>(data.id, data.longId)) shouldBe data
                }
            }
        }
    }
}){
    companion object{
        inline fun <reified T: Any> initData(schema: KeySchema): BTree<List<Any?>, T>{
            val valueSerializer = RowDataSerializerHelper(serializer<T>())
            val keySerializer = MultiColumnKeySerializer(schema)
            val diskManager = DiskManager(StorageConfig, IndexConfig)
            val lruPolicy = MidpointLRUPolicy(MidpointLruConfig)
            val bufferPoolManager = BufferPoolManager(diskManager, lruPolicy, IndexConfig, 100)
            val databaseInitializer = DatabaseInitializer(bufferPoolManager)
            val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
            val storageManager = StorageManager(freeSpaceManager, bufferPoolManager, IndexConfig)
            databaseInitializer.initMetaPage()
            return BTree(
                "test",
                "test table",
                storageManager,
                keySerializer,
                valueSerializer,
                IndexConfig,
            )
        }
    }
}