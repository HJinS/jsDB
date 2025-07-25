package index.btree

import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
            listOf<Number>(4, 23276L),
            listOf<Number>(1, 10L),
            listOf<Number>(2, 12342L),
            listOf<Number>(21, 1231342L)
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
}