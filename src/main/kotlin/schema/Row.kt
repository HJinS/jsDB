package schema

class Row(private val schema: RowSchema, private val values: List<Any?>) {
    operator fun get(columnName: String): Any? = values[schema.columnIndex(columnName)]
    fun toList(): List<Any?> = values
}