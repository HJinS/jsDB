package schema

import index.btree.BTree
import index.serializer.KeySerializer
import index.serializer.ValueSerializer

/**
 * Everything `Table` needs to use one index: the byte-only [btree] itself, bundled with the
 * [keySerializer]/[valueSerializer] that convert between domain values and the bytes [btree]
 * actually stores (it knows nothing about columns or types — see [BTree]'s own doc), plus
 * [metadata] describing the index's shape.
 * */
data class IndexHandle(
    val metadata: IndexRow,
    val btree: BTree,
    val keySerializer: KeySerializer<List<Any?>>,
    val valueSerializer: ValueSerializer<List<Any?>>
){
    val columnNames: List<String> by lazy { metadata.keyColumns.map { it.name } }

    /** Pulls this index's key columns (in [columnNames] order) out of a [Row], ready for [keySerializer]. */
    val extractKey: (Row) -> List<Any?> by lazy {
        { row -> columnNames.map { row[it] } }
    }
}
