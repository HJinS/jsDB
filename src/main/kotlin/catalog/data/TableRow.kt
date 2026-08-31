package catalog.data

data class TableRow(
    val tableId: Long,
    val tableName: String,
    val primaryIndexName: String?
)
