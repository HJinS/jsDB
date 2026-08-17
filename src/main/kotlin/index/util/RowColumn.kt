package index.util

data class RowColumn(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean = true
)
