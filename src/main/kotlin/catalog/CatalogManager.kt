package catalog

import catalog.data.ColumnRaw
import catalog.data.ColumnRow
import catalog.data.IndexRaw
import catalog.data.IndexRow
import catalog.data.TableRaw
import catalog.data.TableRow
import catalog.exception.CatalogException
import config.IndexConfig
import index.btree.BTree
import index.serializer.BinaryRowSerializer
import index.serializer.MultiColumnKeySerializer
import index.util.ColumnType
import index.util.IndexColumn
import storageEngine.StorageManager
import util.requireOrThrow

class CatalogManager(
    metaPageData: MetaPageData,
    private val storageManager: StorageManager,
    private val indexConfig: IndexConfig,
    onTableCatalogRootChanged: (Long) -> Unit,
    onIndexCatalogRootChanged: (Long) -> Unit,
    onColumnCatalogRootChanged: (Long) -> Unit,
) {
    private var tableCatalog = BTree(
        name = CatalogBoot.TABLE_CATALOG_INDEX_NAME,
        targetTable = CatalogBoot.TABLE_CATALOG_NAME,
        storageManager = storageManager,
        keySerializer = MultiColumnKeySerializer(CatalogBoot.TABLE_CATALOG_KEY),
        valueSerializer = BinaryRowSerializer(CatalogBoot.TABLE_CATALOG_ROW),
        indexConfig = indexConfig,
        rootPageId = metaPageData.tableCatalogRootPageId,
        onRootChanged = onTableCatalogRootChanged
    )
    private var indexCatalog = BTree(
        name = CatalogBoot.INDEX_CATALOG_INDEX_NAME,
        targetTable = CatalogBoot.INDEX_CATALOG_NAME,
        storageManager = storageManager,
        keySerializer = MultiColumnKeySerializer(CatalogBoot.INDEX_CATALOG_KEY),
        valueSerializer = BinaryRowSerializer(CatalogBoot.INDEX_CATALOG_ROW),
        indexConfig = indexConfig,
        rootPageId = metaPageData.indexCatalogRootPageId,
        onRootChanged = onIndexCatalogRootChanged
    )
    private var columnCatalog = BTree(
        name = CatalogBoot.COLUMN_CATALOG_INDEX_NAME,
        targetTable = CatalogBoot.COLUMN_CATALOG_NAME,
        storageManager = storageManager,
        keySerializer = MultiColumnKeySerializer(CatalogBoot.COLUMN_CATALOG_KEY),
        valueSerializer = BinaryRowSerializer(CatalogBoot.COLUMN_CATALOG_ROW),
        indexConfig = indexConfig,
        rootPageId = metaPageData.columnCatalogRootPageId,
        onRootChanged = onColumnCatalogRootChanged
    )

    fun resolveIndex(name: String): IndexRow?{
        val data = indexCatalog.search(listOf(name)) ?: return null
        return IndexRaw(data).toRow()
    }

    fun resolveColumn(tableId: Long, ordinal: Int): ColumnRow?{
        val data = columnCatalog.search(listOf(tableId, ordinal)) ?: return null
        return ColumnRaw(data).toRow()
    }

    fun resolveTable(name: String): TableRow?{
        val data = tableCatalog.search(listOf(name)) ?: return null
        return TableRaw(data).toRow()
    }

    fun registerNewColumn(tableId: Long, ordinal: Int, name: String, type: String, nullable: Boolean): ColumnRow{
        val columnType = try{
            ColumnType.valueOf(type)
        } catch (e: IllegalArgumentException){
            throw CatalogException.CorruptedCatalogException(CatalogBoot.COLUMN_CATALOG_NAME, e)
        }
        columnCatalog.insert(
            listOf(tableId, ordinal),
            listOf(tableId, ordinal, name, type, nullable)
        )
        return ColumnRow(tableId, ordinal, name, columnType, nullable)
    }

    fun registerNewTable(tableId: Long, tableName: String, primaryIndexId: Long?): TableRow{
        tableCatalog.insert(
            listOf(tableName),
            listOf(tableId, tableName, primaryIndexId)
        )
        return TableRow(tableId, tableName, primaryIndexId)
    }

    fun registerNewIndex(
        indexId: Long,
        indexName: String,
        tableId: Long,
        rootPageId: Long?,
        isPrimary: Boolean,
        isUnique: Boolean,
        keyColumns: List<IndexColumn>
    ): IndexRow{
        indexCatalog.insert(
            listOf(indexName),
            listOf(indexId, indexName, tableId, rootPageId, isPrimary, isUnique, keyColumns.encodeKeyColumns())
        )
        return IndexRow(indexId, indexName, tableId, rootPageId, isPrimary, isUnique, keyColumns)
    }

    fun updateIndexRootPageId(indexName: String, rootPageId: Long){
        val value = indexCatalog.search(listOf(indexName))
        requireOrThrow(value != null){ CatalogException.IndexNotFound(indexName) }
        val indexRow = IndexRaw(value).toRow()
        val newRow = indexRow.copy(rootPageId = rootPageId)
        val rowList = newRow.toList()
        indexCatalog.update(
            listOf(indexName),
            listOf(indexName),
            rowList
        )
    }
}