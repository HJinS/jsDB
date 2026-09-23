package schema

/** One decoded row: [values] in [schema] column order, addressable by name via [get]. */
class Row(private val schema: RowSchema, private val values: List<Any?>) {
    /** @throws exception.DatabaseException.UndefinedColumn if [columnName] isn't in [schema]. */
    operator fun get(columnName: String): Any? = values[schema.columnIndex(columnName)]

    /** The row's raw positional values, e.g. for re-serializing via `ValueSerializer`. */
    fun toList(): List<Any?> = values
}