package catalog.data

import index.util.ColumnType

data class ColumnRow(
    val tableID: Long,
    val ordinal: Int,
    val name: String,
    val type: ColumnType,
    val nullable: Boolean
)
