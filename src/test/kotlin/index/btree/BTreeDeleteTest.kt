package index.btree

import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertTrue



/**
 * TODO key packing, compare 부분 int 숫자 키워서 비교 테스트 케이스 추가
 * - binarySearch 결과가 게속 이상함
 * */
class BTreeDeleteTest {
    @Test
    @DisplayName("Given field(int, Long), when insert keys, then traverse result will be lexicographic and packedKey result should be same")
    fun `insert int long keys and verify sorted order`() {
        val schema = KeySchema(
            listOf(
                Column("count", ColumnType.INT, descending = false),
                Column("largeCount", ColumnType.LONG, descending = false)
            )
        )

        val btree = BTree("test", "test table", schema, 3, true)

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
            listOf<Number>(4, 23276L)
        )

        for (key in keys) {
            btree.insert(key)
        }
        btree.printTree()
        println("---------------------------------------------------")
        println("delete key 523 123932L")
        btree.delete(listOf<Number>(523, 123932L))
        btree.printTree()
        println("---------------------------------------------------")
        println("delete key 235 123123932L")
        btree.delete(listOf<Number>(235, 123123932L))
        btree.printTree()

        val allKeys = btree.traverse()
        val expectedResults = listOf(
            listOf<Number>(1, 10L),
            listOf<Number>(2, 12342L),
            listOf<Number>(3, 4L),
            listOf<Number>(3, 12342L),
            listOf<Number>(4, 1032L),
            listOf<Number>(4, 23276L),
            listOf<Number>(5, 50L),
            listOf<Number>(5, 123932L)
        )
        assertTrue { expectedResults == allKeys.toList().map{ it.second } }
        btree.printTree()

        btree.delete(listOf<Number>(1, 10L))
    }

    @Test
    @DisplayName("Given field(int(desc), Long), when insert keys, then traverse result will be lexicographic and packedKey result should be same")
    fun `insert int long keys and verify sorted order desc`() {
        val schema = KeySchema(
            listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = false)
            )
        )

        val btree = BTree("test", "test table", schema, 2, true)

        val keys = listOf(
            listOf<Number>(1, 10L),
            listOf<Number>(5, 50L),
            listOf<Number>(3, 4L),
            listOf<Number>(4, 1032L),
            listOf<Number>(2, 12342L),
            listOf<Number>(5, 123932L),
            listOf<Number>(3, 12342L),
            listOf<Number>(4, 23276L)
        )

        for (key in keys) {
            btree.insert(key)
        }

        val allKeys = btree.traverse()
        val expectedResults = listOf(
            listOf<Number>(5, 50L),
            listOf<Number>(5, 123932L),
            listOf<Number>(4, 1032L),
            listOf<Number>(4, 23276L),
            listOf<Number>(3, 4L),
            listOf<Number>(3, 12342L),
            listOf<Number>(2, 12342L),
            listOf<Number>(1, 10L)
        )
        assertTrue { expectedResults == allKeys.toList().map{ it.second } }
        btree.printTree()
    }

    @Test
    @DisplayName("Given field(int(desc), Long(desc)), when insert keys, then traverse result will be lexicographic and packedKey result should be same")
    fun `insert int long keys and verify sorted order desc2`() {
        val schema = KeySchema(
            listOf(
                Column("count", ColumnType.INT, descending = true),
                Column("largeCount", ColumnType.LONG, descending = true)
            )
        )

        val btree = BTree("test", "test table", schema, 2, true)

        val keys = listOf(
            listOf<Number>(1, 10L),
            listOf<Number>(5, 50L),
            listOf<Number>(3, 4L),
            listOf<Number>(4, 1032L),
            listOf<Number>(2, 12342L),
            listOf<Number>(5, 123932L),
            listOf<Number>(3, 12342L),
            listOf<Number>(4, 23276L)
        )

        for (key in keys) {
            btree.insert(key)
        }

        val allKeys = btree.traverse()
        val expectedResults = listOf(
            listOf<Number>(5, 123932L),
            listOf<Number>(5, 50L),
            listOf<Number>(4, 23276L),
            listOf<Number>(4, 1032L),
            listOf<Number>(3, 12342L),
            listOf<Number>(3, 4L),
            listOf<Number>(2, 12342L),
            listOf<Number>(1, 10L)
        )
        assertTrue { expectedResults == allKeys.toList().map{ it.second } }
        btree.printTree()
    }

    @Test
    @DisplayName("Given field(string, local date), when insert keys, then traverse result will be lexicographic and packedKey result should be same")
    fun `insert string local date keys and verify sorted order`() {
        val schema = KeySchema(
            listOf(
                Column("name", ColumnType.STRING, descending = false),
                Column("date", ColumnType.LOCAL_DATE, descending = false)
            )
        )

        val btree = BTree("test", "test table", schema, 2, true)

        val keys = listOf(
            listOf("Ava", LocalDate.of(2025, 4, 30)),
            listOf("Grace", LocalDate.of(2024, 3, 20)),
            listOf("Ava", LocalDate.of(2019, 12, 25)),
            listOf("Elijah", LocalDate.of(1997, 12, 25)),
            listOf("Faith", LocalDate.of(2022, 1, 18)),
            listOf("Grace", LocalDate.of(2020, 1, 30)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Chloe", LocalDate.of(2019, 12, 25))
        )

        for (key in keys) {
            btree.insert(key)
        }

        val allKeys = btree.traverse()
        val expectedResults = listOf(
            listOf("Ava", LocalDate.of(2019, 12, 25)),
            listOf("Ava", LocalDate.of(2025, 4, 30)),
            listOf("Chloe", LocalDate.of(2019, 12, 25)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Elijah", LocalDate.of(1997, 12, 25)),
            listOf("Faith", LocalDate.of(2022, 1, 18)),
            listOf("Grace", LocalDate.of(2020, 1, 30)),
            listOf("Grace", LocalDate.of(2024, 3, 20))
        )
        assertTrue { expectedResults == allKeys.toList().map{ it.second } }
        btree.printTree()
    }

    @Test
    @DisplayName("Given field(string(desc), local date), when insert keys, then traverse result will be lexicographic and packedKey result should be same")
    fun `insert string local date keys and verify sorted order desc`() {
        val schema = KeySchema(
            listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("date", ColumnType.LOCAL_DATE, descending = false)
            )
        )

        val btree = BTree("test", "test table", schema, 2, true)

        val keys = listOf(
            listOf("Ava", LocalDate.of(2025, 4, 30)),
            listOf("Grace", LocalDate.of(2024, 3, 20)),
            listOf("Ava", LocalDate.of(2019, 12, 25)),
            listOf("Elijah", LocalDate.of(1997, 12, 25)),
            listOf("Faith", LocalDate.of(2022, 1, 18)),
            listOf("Grace", LocalDate.of(2020, 1, 30)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Chloe", LocalDate.of(2019, 12, 25))
        )

        for (key in keys) {
            btree.insert(key)
        }

        val allKeys = btree.traverse()
        val expectedResults = listOf(
            listOf("Grace", LocalDate.of(2020, 1, 30)),
            listOf("Grace", LocalDate.of(2024, 3, 20)),
            listOf("Faith", LocalDate.of(2022, 1, 18)),
            listOf("Elijah", LocalDate.of(1997, 12, 25)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Chloe", LocalDate.of(2019, 12, 25)),
            listOf("Ava", LocalDate.of(2019, 12, 25)),
            listOf("Ava", LocalDate.of(2025, 4, 30))
        )
        assertTrue { expectedResults == allKeys.toList().map{ it.second } }
        btree.printTree()
    }

    @Test
    @DisplayName("Given field(string(desc), local date(desc)), when insert keys, then traverse result will be lexicographic and packedKey result should be same")
    fun `insert string local date keys and verify sorted order desc 2`() {
        val schema = KeySchema(
            listOf(
                Column("name", ColumnType.STRING, descending = true),
                Column("date", ColumnType.LOCAL_DATE, descending = true)
            )
        )

        val btree = BTree("test", "test table", schema, 2, true)

        val keys = listOf(
            listOf("Ava", LocalDate.of(2025, 4, 30)),
            listOf("Grace", LocalDate.of(2024, 3, 20)),
            listOf("Ava", LocalDate.of(2019, 12, 25)),
            listOf("Elijah", LocalDate.of(1997, 12, 25)),
            listOf("Faith", LocalDate.of(2022, 1, 18)),
            listOf("Grace", LocalDate.of(2020, 1, 30)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Chloe", LocalDate.of(2019, 12, 25))
        )

        for (key in keys) {
            btree.insert(key)
        }

        val allKeys = btree.traverse()
        val expectedResults = listOf(
            listOf("Grace", LocalDate.of(2024, 3, 20)),
            listOf("Grace", LocalDate.of(2020, 1, 30)),
            listOf("Faith", LocalDate.of(2022, 1, 18)),
            listOf("Elijah", LocalDate.of(1997, 12, 25)),
            listOf("Daniel", LocalDate.of(2018, 4, 9)),
            listOf("Chloe", LocalDate.of(2019, 12, 25)),
            listOf("Ava", LocalDate.of(2025, 4, 30)),
            listOf("Ava", LocalDate.of(2019, 12, 25))

        )
        assertTrue { expectedResults == allKeys.toList().map{ it.second } }
        btree.printTree()
    }
}