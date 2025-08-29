package index.btree

import index.comparator.MultiColumnKeyComparator
import index.serializer.LocalDateSerializer
import index.serializer.MultiColumnKeySerializer
import index.serializer.RowDataSerializer
import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import java.time.LocalDate


class BTreeSearchTest: BehaviorSpec({

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

    Given("A Tree with schema $schema"){
        listOf(
            listOf<Number>(523, 123932L) to IDData(523, 123932L),
            listOf<Number>(1, 10L) to IDData(1, 10L),
            listOf<Number>(1, 20L) to null,
            listOf<Number>(235, 123123902132L) to null,
        ).forEachIndexed {
            index, parameter ->
            val searchKey = parameter.first
            val expectedResult = parameter.second
            When("Search key $searchKey"){
                val result = btree.search(searchKey)
                Then("Trace result should be $expectedResult"){
                    result shouldBe expectedResult
                }

            }
        }
    }

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
        btree2.insert(key, value)
    }

    Given("A Tree with schema $schema2"){
        listOf(
            listOf("ElijahKim", LocalDate.of(1997, 12, 25)) to UserData("ElijahKim", LocalDate.of(1997, 12, 25)),
            listOf("Lucas", LocalDate.of(1697, 12, 25)) to UserData("Lucas", LocalDate.of(1697, 12, 25)),
            listOf("Chloed", LocalDate.of(2020, 12, 26)) to null,
            listOf("Ava", LocalDate.of(2019, 11, 25)) to null,
        ).forEachIndexed {
                index, parameter ->
            val searchKey = parameter.first
            val expectedResult = parameter.second
            When("Search key $searchKey"){
                val result = btree2.search(searchKey)
                Then("Trace result should be $expectedResult"){
                    result shouldBe expectedResult
                }
            }
        }
    }
}){
    companion object{
        @Serializable
        data class IDData(val id: Int, val longId: Long)

        @Serializable
        data class UserData(
            val name: String,
            @Serializable(with = LocalDateSerializer::class)
            val birthDate: LocalDate
        )

        val schema = KeySchema(listOf(
            Column("count", ColumnType.INT, descending = false),
            Column("largeCount", ColumnType.LONG, descending = false)
        ))

        val schema2 = KeySchema(listOf(
            Column("name", ColumnType.STRING, descending = false),
            Column("date", ColumnType.LOCAL_DATE, descending = false)
        ))

        val idValueSerializer = RowDataSerializer(IDData::class)
        val userDataSerializer = RowDataSerializer(UserData::class)

        val keySerializer2 = MultiColumnKeySerializer(schema2)
        val keySerializer = MultiColumnKeySerializer(schema)

        val btree = BTree(
            "test",
            "test table",
            keySerializer,
            idValueSerializer,
            MultiColumnKeyComparator(schema),
            2,
            true
        )

        val btree2 = BTree(
            "test",
            "test table",
            keySerializer2,
            userDataSerializer,
            MultiColumnKeyComparator(schema2),
            2,
            true
        )
    }
}