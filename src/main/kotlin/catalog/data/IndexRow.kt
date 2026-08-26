package catalog.data

import index.util.IndexColumn

data class IndexRow(
    val indexId: Long,
    val indexName: String,
    val tableId: Long,
    val rootPageId: Long?,
    val isPrimary: Boolean,
    val isUnique: Boolean,
    val keyColumns: List<IndexColumn>
)
