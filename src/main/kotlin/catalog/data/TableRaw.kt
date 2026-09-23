package catalog.data

/** A table catalog row, still untyped [values] straight out of `BinaryRowSerializer.deserialize`. See `catalog.toRow()`. */
@JvmInline
value class TableRaw(val values: List<Any?>)