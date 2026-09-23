package schema

/**
 * Builds the [RowSchema] for an index-organized table's primary index value (see
 * [database.DataBase.resolveIndexValueSchema]): one [RowColumn] per key column, always
 * non-nullable — primary key columns can never be null.
 * */
fun IndexKeySchema.toPrimaryRowSchema()
    = RowSchema(indexColumns.map { RowColumn(name = it.name, type = it.type, nullable = false) })


/** Drops [ColumnRow.tableId]/[ColumnRow.ordinal] (catalog-only bookkeeping) to get a plain [RowColumn]. */
fun ColumnRow.toRowColumn()
    = RowColumn(name=name, type=type, nullable=nullable)
