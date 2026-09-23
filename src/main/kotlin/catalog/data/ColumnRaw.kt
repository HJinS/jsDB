package catalog.data

/**
 * A column catalog row, still untyped [values] straight out of `BinaryRowSerializer.deserialize`.
 * See `catalog.toRow()`.
 */
@JvmInline value class ColumnRaw(val values: List<Any?>)

