package catalog.data

import index.util.ColumnType

data class ColumnRow(
    val tableId: Long,
    val ordinal: Int,
    val name: String,
    val type: ColumnType,
    val nullable: Boolean
)
