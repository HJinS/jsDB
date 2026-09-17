package schema

import index.btree.BTree
import index.serializer.KeySerializer
import index.serializer.ValueSerializer

data class IndexHandle(
    val metadata: IndexRow,
    val btree: BTree,
    val keySerializer: KeySerializer<List<Any?>>,
    val valueSerializer: ValueSerializer<List<Any?>>
){
    val columnNames: List<String> by lazy { metadata.keyColumns.map { it.name } }

    val extractKey: (Row) -> List<Any?> by lazy {
        { row -> columnNames.map { row[it] } }
    }
}
