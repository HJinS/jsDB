package index.util

import catalog.data.ColumnRow

fun IndexKeySchema.toPrimaryRowSchema()
    = RowSchema(indexColumns.map { RowColumn(name = it.name, type = it.type, nullable = false) })


fun ColumnRow.toRowColumn()
    = RowColumn(name=name, type=type, nullable=nullable)