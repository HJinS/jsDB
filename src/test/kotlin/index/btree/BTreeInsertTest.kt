package index.btree

import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test


class BTreeInsertTest {
    @Test
    @DisplayName("")
    fun `insert keys and verify sorted order`() {
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
            listOf<Number>(5, 123932L),
            listOf<Number>(3, 12342L),
            listOf<Number>(4, 23276L)
        )

        for (key in keys) {
            btree.insert(key)
        }

        val allKeys = btree.traverse()
        print("Test End")
        print(allKeys)
    }
}
