package catalog

import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import schema.RowColumn
import schema.RowSchema

/**
 * Fixed schemas and names for the three system catalog tables ([CatalogManager] owns one
 * [index.btree.BTree] per `*_CATALOG_KEY`/`*_CATALOG_ROW` pair). These are the catalog's *own*
 * schemas — unrelated to any user table's columns.
 *
 * [INDEX_COLUMN_ROW] is different from the other three: it's not a catalog table's row schema on
 * its own, but the per-element schema [encodeKeyColumns]/[decodeKeyColumns] use to pack the whole
 * `keyColumns` list into the single `BYTES` field of one [INDEX_CATALOG_ROW] row.
 * */
object CatalogBoot {
    val TABLE_CATALOG_ROW = RowSchema(
        listOf(
            RowColumn("tableId", ColumnType.LONG, false),
            RowColumn("tableName", ColumnType.STRING, false),
            RowColumn("primaryIndexName", ColumnType.STRING, true)
        )
    )
    val TABLE_CATALOG_KEY = IndexKeySchema(
        listOf(
            IndexColumn("tableName", ColumnType.STRING, false)
        )
    )
    const val TABLE_CATALOG_NAME = "TABLE_CATALOG"
    const val TABLE_CATALOG_INDEX_NAME = "TABLE_CATALOG_IDX"

    val COLUMN_CATALOG_ROW = RowSchema(
        listOf(
            RowColumn("tableId", ColumnType.LONG, false),
            RowColumn("ordinal", ColumnType.INT, false),
            RowColumn("name", ColumnType.STRING, false),
            RowColumn("type", ColumnType.STRING, false),
            RowColumn("nullable", ColumnType.BOOLEAN, false)
        )
    )
    val COLUMN_CATALOG_KEY = IndexKeySchema(
        listOf(
            IndexColumn("tableId", ColumnType.LONG, false),
            IndexColumn("ordinal", ColumnType.INT, false)
        )
    )
    const val COLUMN_CATALOG_NAME = "COLUMN_CATALOG"
    const val COLUMN_CATALOG_INDEX_NAME = "COLUMN_CATALOG_IDX"

    val INDEX_CATALOG_ROW = RowSchema(
        listOf(
            RowColumn("indexId", ColumnType.LONG, false),
            RowColumn("indexName", ColumnType.STRING, false),
            RowColumn("tableName", ColumnType.STRING, false),
            RowColumn("rootPageId", ColumnType.LONG, true),
            RowColumn("isPrimary", ColumnType.BOOLEAN, false),
            RowColumn("isUnique", ColumnType.BOOLEAN, false),
            RowColumn("keyColumns", ColumnType.BYTES, false),
        )
    )
    val INDEX_CATALOG_KEY = IndexKeySchema(
        listOf(
            IndexColumn("indexName", ColumnType.STRING, false)
        )
    )
    const val INDEX_CATALOG_NAME = "INDEX_CATALOG"
    const val INDEX_CATALOG_INDEX_NAME = "INDEX_CATALOG_IDX"

    val INDEX_COLUMN_ROW = RowSchema(listOf(
        RowColumn("name", ColumnType.STRING, nullable = false),
        RowColumn("type", ColumnType.STRING, nullable = false),
        RowColumn("descending", ColumnType.BOOLEAN, nullable = false),
        RowColumn("localeTag", ColumnType.STRING, nullable = true),
        RowColumn("collationStrength", ColumnType.INT, nullable = true)
    ))
}