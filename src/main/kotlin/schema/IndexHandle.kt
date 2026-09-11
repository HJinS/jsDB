package schema

import index.btree.BTree

data class IndexHandle(
    val metadata: IndexRow,
    val btree: BTree<List<Any?>, List<Any?>>
){
    val columnNames: List<String> by lazy { metadata.keyColumns.map { it.name } }

    val extractKey: (Row) -> List<Any?> by lazy {
        { row -> columnNames.map { row[it] } }
    }
}
