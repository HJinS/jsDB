package schema

data class IndexRow(
    val indexId: Long,
    val indexName: String,
    val tableName: String,
    val rootPageId: Long?,
    val isPrimary: Boolean,
    val isUnique: Boolean,
    val keyColumns: List<IndexColumn>
)
