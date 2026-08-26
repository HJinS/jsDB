package catalog.data

data class TableRow(
    val tableId: Long,
    val tableName: String,
    val primaryIndexId: Long?
)
