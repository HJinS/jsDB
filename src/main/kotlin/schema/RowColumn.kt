package schema

data class RowColumn(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean = true,
    val primaryKeyOrder: Int? = null
)
