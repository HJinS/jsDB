package index.btree

import index.comparator.MultiColumnKeyComparator
import index.serializer.LocalDateSerializer
import index.serializer.MultiColumnKeySerializer
import index.serializer.RowDataSerializer
import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import java.time.LocalDate


class BTreeInsertTest: FunSpec({

    @Serializable
    data class IDData(val id: Int, val longId: Long)

    @Serializable
    data class UserData(
        val name: String,
        @Serializable(with = LocalDateSerializer::class)
        val birthDate: LocalDate
    )

    val idValueSerializer = RowDataSerializer(IDData::class)

    val userDataSerializer = RowDataSerializer(UserData::class)

    listOf(
        Triple(
            listOf(
                listOf<Number>(1, 10L),
                listOf<Number>(5, 50L),
                listOf<Number>(3, 4L),
                listOf<Number>(4, 1032L),
                listOf<Number>(2, 12342L),
                listOf<Number>(5, 123932L),
                listOf<Number>(3, 12342L),
                listOf<Number>(4, 23276L)
            ),
            listOf(
                IDData(1, 10L),
                IDData(2, 12342L),
                IDData(3, 4L),
                IDData(3, 12342L),
                IDData(4, 1032L),
                IDData(4, 23276L),
                IDData(5, 50L),
                IDData(5, 123932L)
            ),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf(
                listOf<Number>(1, 10L),
                listOf<Number>(5, 50L),
                listOf<Number>(3, 4L),
                listOf<Number>(4, 1032L),
                listOf<Number>(2, 12342L),
                listOf<Number>(5, 123932L),
                listOf<Number>(3, 12342L),
                listOf<Number>(4, 23276L)
            ),
            listOf(
                IDData(5, 50L),
                IDData(5, 123932L),
                IDData(4, 1032L),
                IDData(4, 23276L),
                IDData(3, 4L),
                IDData(3, 12342L),
                IDData(2, 12342L),
                IDData(1, 10L)
            ),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            ))
        ),
        Triple(
            listOf(
                listOf<Number>(1, 10L),
                listOf<Number>(5, 50L),
                listOf<Number>(3, 4L),
                listOf<Number>(4, 1032L),
                listOf<Number>(2, 12342L),
                listOf<Number>(5, 123932L),
                listOf<Number>(3, 12342L),
                listOf<Number>(4, 23276L)
            ),
            listOf(
                IDData(5, 123932L),
                IDData(5, 50L),
                IDData(4, 23276L),
                IDData(4, 1032L),
                IDData(3, 12342L),
                IDData(3, 4L),
                IDData(2, 12342L),
                IDData(1, 10L)
            ),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = true)
            ))
        ),
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] After inserting, b+tree leaf nodes should be lexicographic order"){

            val keySerializer = MultiColumnKeySerializer(parameter.third)
            val btree = BTree(
                "test",
                "test table",
                keySerializer,
                idValueSerializer,
                MultiColumnKeyComparator(parameter.third),
                2,
                true
            )

            val keys = parameter.first

            for (key in keys) {
                val value = IDData(
                    id = key[0] as Int,
                    longId = key[1] as Long
                )
                btree.insert(key, value)
            }

            val allKeys = btree.traverse()
            val expectedResults = parameter.second
            allKeys.toList().map{ it.second } shouldBe expectedResults
            btree.printTree()
        }
    }

    listOf(
        Triple(
            listOf(
                listOf("Ava", LocalDate.of(2025, 4, 30)),
                listOf("Grace", LocalDate.of(2024, 3, 20)),
                listOf("Ava", LocalDate.of(2019, 12, 25)),
                listOf("Elijah", LocalDate.of(1997, 12, 25)),
                listOf("Faith", LocalDate.of(2022, 1, 18)),
                listOf("Grace", LocalDate.of(2020, 1, 30)),
                listOf("Daniel", LocalDate.of(2018, 4, 9)),
                listOf("Chloe", LocalDate.of(2019, 12, 25))
            ),
            listOf(
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Ava", LocalDate.of(2025, 4, 30)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Elijah", LocalDate.of(1997, 12, 25)),
                UserData("Faith", LocalDate.of(2022, 1, 18)),
                UserData("Grace", LocalDate.of(2020, 1, 30)),
                UserData("Grace", LocalDate.of(2024, 3, 20))
            ),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("date", ColumnType.LOCAL_DATE, descending = false)
            ))
        ),
        Triple(
            listOf(
                listOf("Ava", LocalDate.of(2025, 4, 30)),
                listOf("Grace", LocalDate.of(2024, 3, 20)),
                listOf("Ava", LocalDate.of(2019, 12, 25)),
                listOf("Elijah", LocalDate.of(1997, 12, 25)),
                listOf("Faith", LocalDate.of(2022, 1, 18)),
                listOf("Grace", LocalDate.of(2020, 1, 30)),
                listOf("Daniel", LocalDate.of(2018, 4, 9)),
                listOf("Chloe", LocalDate.of(2019, 12, 25))
            ),
            listOf(
                UserData("Grace", LocalDate.of(2020, 1, 30)),
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Faith", LocalDate.of(2022, 1, 18)),
                UserData("Elijah", LocalDate.of(1997, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Ava", LocalDate.of(2019, 12, 25)),
                UserData("Ava", LocalDate.of(2025, 4, 30))
            ),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("date", ColumnType.LOCAL_DATE, descending = false)
            ))
        ),
        Triple(
            listOf(
                listOf("Ava", LocalDate.of(2025, 4, 30)),
                listOf("Grace", LocalDate.of(2024, 3, 20)),
                listOf("Ava", LocalDate.of(2019, 12, 25)),
                listOf("Elijah", LocalDate.of(1997, 12, 25)),
                listOf("Faith", LocalDate.of(2022, 1, 18)),
                listOf("Grace", LocalDate.of(2020, 1, 30)),
                listOf("Daniel", LocalDate.of(2018, 4, 9)),
                listOf("Chloe", LocalDate.of(2019, 12, 25))
            ),
            listOf(
                UserData("Grace", LocalDate.of(2024, 3, 20)),
                UserData("Grace", LocalDate.of(2020, 1, 30)),
                UserData("Faith", LocalDate.of(2022, 1, 18)),
                UserData("Elijah", LocalDate.of(1997, 12, 25)),
                UserData("Daniel", LocalDate.of(2018, 4, 9)),
                UserData("Chloe", LocalDate.of(2019, 12, 25)),
                UserData("Ava", LocalDate.of(2025, 4, 30)),
                UserData("Ava", LocalDate.of(2019, 12, 25))

            ),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("date", ColumnType.LOCAL_DATE, descending = true)
            ))
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] After inserting, b+tree leaf nodes should be lexicographic order"){
            val keySerializer = MultiColumnKeySerializer(parameter.third)
            val btree = BTree(
                "test",
                "test table",
                keySerializer,
                userDataSerializer,
                MultiColumnKeyComparator(parameter.third),
                2,
                true
            )

            val keys = parameter.first

            for (key in keys) {
                val value = UserData(
                    name = key[0] as String,
                    birthDate = key[1] as LocalDate,
                )
                btree.insert(key, value)
            }

            val allKeys = btree.traverse()
            val expectedResults = parameter.second
            allKeys.toList().map{ it.second } shouldBe expectedResults
            btree.printTree()
        }
    }

})