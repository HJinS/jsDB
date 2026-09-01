package catalog

import catalog.data.ColumnRaw
import catalog.data.ColumnRow
import catalog.data.IndexRaw
import catalog.data.IndexRow
import catalog.data.TableRaw
import catalog.data.TableRow
import catalog.exception.CatalogException
import index.util.ColumnType
import kotlin.Long

fun ColumnRaw.toRow(): ColumnRow {
    return try {
        ColumnRow(
            tableId = values[0] as Long,
            ordinal = values[1] as Int,
            name = values[2] as String,
            type = ColumnType.valueOf(values[3] as String),
            nullable = values[4] as Boolean
        )
    } catch (e: IndexOutOfBoundsException){
        throw CatalogException.CorruptedCatalogException(CatalogBoot.COLUMN_CATALOG_NAME, e)
    } catch (e: ClassCastException){
        throw CatalogException.CorruptedCatalogException(CatalogBoot.COLUMN_CATALOG_NAME, e)
    } catch (e: IllegalArgumentException){
        throw CatalogException.CorruptedCatalogException(CatalogBoot.COLUMN_CATALOG_NAME, e)
    }
}

fun TableRaw.toRow(): TableRow {
    return try{
        TableRow(
            tableId = values[0] as Long,
            tableName = values[1] as String,
            primaryIndexName = values[2] as String?
        )
    } catch (e: IndexOutOfBoundsException){
        throw CatalogException.CorruptedCatalogException(CatalogBoot.TABLE_CATALOG_NAME, e)
    } catch (e: ClassCastException){
        throw CatalogException.CorruptedCatalogException(CatalogBoot.TABLE_CATALOG_NAME, e)
    }
}

fun IndexRaw.toRow(): IndexRow {
    return try{
        IndexRow(
            indexId = values[0] as Long,
            indexName = values[1] as String,
            tableName = values[2] as String,
            rootPageId = values[3] as Long?,
            isPrimary = values[4] as Boolean,
            isUnique = values[5] as Boolean,
            keyColumns = (values[6] as ByteArray).decodeKeyColumns()
        )
    } catch (e: IndexOutOfBoundsException){
        throw CatalogException.CorruptedCatalogException(CatalogBoot.INDEX_CATALOG_NAME, e)
    } catch (e: ClassCastException){
        throw CatalogException.CorruptedCatalogException(CatalogBoot.INDEX_CATALOG_NAME, e)
    }
}

fun IndexRow.toList(): List<Any?>
    = listOf(indexId, indexName, tableName, rootPageId, isPrimary, isUnique, keyColumns.encodeKeyColumns())

fun TableRow.toList(): List<Any?>
    = listOf(tableId, tableName, primaryIndexName)