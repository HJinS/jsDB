package catalog.data

/** An index catalog row, still untyped [values] straight out of `BinaryRowSerializer.deserialize`. See `catalog.toRow()`. */
@JvmInline
value class IndexRaw(val values: List<Any?>)