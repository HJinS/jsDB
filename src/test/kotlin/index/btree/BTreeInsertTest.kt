package index.btree

import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate


class BTreeInsertTest: FunSpec({
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
                listOf<Number>(1, 10L),
                listOf<Number>(2, 12342L),
                listOf<Number>(3, 4L),
                listOf<Number>(3, 12342L),
                listOf<Number>(4, 1032L),
                listOf<Number>(4, 23276L),
                listOf<Number>(5, 50L),
                listOf<Number>(5, 123932L)
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
                listOf<Number>(5, 50L),
                listOf<Number>(5, 123932L),
                listOf<Number>(4, 1032L),
                listOf<Number>(4, 23276L),
                listOf<Number>(3, 4L),
                listOf<Number>(3, 12342L),
                listOf<Number>(2, 12342L),
                listOf<Number>(1, 10L)
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
                listOf<Number>(5, 123932L),
                listOf<Number>(5, 50L),
                listOf<Number>(4, 23276L),
                listOf<Number>(4, 1032L),
                listOf<Number>(3, 12342L),
                listOf<Number>(3, 4L),
                listOf<Number>(2, 12342L),
                listOf<Number>(1, 10L)
            ),
            KeySchema(listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = true)
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
                listOf("Ava", LocalDate.of(2019, 12, 25)),
                listOf("Ava", LocalDate.of(2025, 4, 30)),
                listOf("Chloe", LocalDate.of(2019, 12, 25)),
                listOf("Daniel", LocalDate.of(2018, 4, 9)),
                listOf("Elijah", LocalDate.of(1997, 12, 25)),
                listOf("Faith", LocalDate.of(2022, 1, 18)),
                listOf("Grace", LocalDate.of(2020, 1, 30)),
                listOf("Grace", LocalDate.of(2024, 3, 20))
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
                listOf("Grace", LocalDate.of(2020, 1, 30)),
                listOf("Grace", LocalDate.of(2024, 3, 20)),
                listOf("Faith", LocalDate.of(2022, 1, 18)),
                listOf("Elijah", LocalDate.of(1997, 12, 25)),
                listOf("Daniel", LocalDate.of(2018, 4, 9)),
                listOf("Chloe", LocalDate.of(2019, 12, 25)),
                listOf("Ava", LocalDate.of(2019, 12, 25)),
                listOf("Ava", LocalDate.of(2025, 4, 30))
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
                listOf("Grace", LocalDate.of(2024, 3, 20)),
                listOf("Grace", LocalDate.of(2020, 1, 30)),
                listOf("Faith", LocalDate.of(2022, 1, 18)),
                listOf("Elijah", LocalDate.of(1997, 12, 25)),
                listOf("Daniel", LocalDate.of(2018, 4, 9)),
                listOf("Chloe", LocalDate.of(2019, 12, 25)),
                listOf("Ava", LocalDate.of(2025, 4, 30)),
                listOf("Ava", LocalDate.of(2019, 12, 25))

            ),
            KeySchema(listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("date", ColumnType.LOCAL_DATE, descending = true)
            ))
        )
    ).forEachIndexed{ index, parameter ->
        test("[Test $index] After inserting, b+tree leaf nodes should be lexicographic order"){
            val btree = BTree("test", "test table", parameter.third, 2, true)

            val keys = parameter.first

            for (key in keys) {
                btree.insert(key)
            }

            val allKeys = btree.traverse()
            val expectedResults = parameter.second
            allKeys.toList().map{ it.second } shouldBe expectedResults
            btree.printTree()
        }
    }
})