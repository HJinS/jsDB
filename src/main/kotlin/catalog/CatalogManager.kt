package catalog

import catalog.data.ColumnRaw
import schema.ColumnRow
import catalog.data.IndexRaw
import schema.IndexRow
import catalog.data.TableRaw
import schema.TableRow
import exception.CatalogException
import config.IndexConfig
import index.btree.BTree
import index.serializer.BinaryRowSerializer
import index.serializer.MultiColumnKeySerializer
import schema.ColumnType
import schema.IndexColumn
import schema.MetaPageData
import storageEngine.StorageManager
import util.EntityType
import util.SQLErrorDetail
import util.requireOrThrow

class CatalogManager(
    metaPageData: MetaPageData,
    private val storageManager: StorageManager,
    private val indexConfig: IndexConfig,
    onTableCatalogRootChanged: (Long) -> Unit,
    onIndexCatalogRootChanged: (Long) -> Unit,
    onColumnCatalogRootChanged: (Long) -> Unit,
) {
    private val tableCatalogKeySerializer = MultiColumnKeySerializer(CatalogBoot.TABLE_CATALOG_KEY)
    private val tableCatalogValueSerializer = BinaryRowSerializer(CatalogBoot.TABLE_CATALOG_ROW)

    private val indexCatalogKeySerializer = MultiColumnKeySerializer(CatalogBoot.INDEX_CATALOG_KEY)
    private val indexCatalogValueSerializer = BinaryRowSerializer(CatalogBoot.INDEX_CATALOG_ROW)

    private val columnCatalogKeySerializer = MultiColumnKeySerializer(CatalogBoot.COLUMN_CATALOG_KEY)
    private val columnCatalogValueSerializer = BinaryRowSerializer(CatalogBoot.COLUMN_CATALOG_ROW)



    private var tableCatalog = BTree(
        name = CatalogBoot.TABLE_CATALOG_INDEX_NAME,
        targetTable = CatalogBoot.TABLE_CATALOG_NAME,
        storageManager = storageManager,
        indexConfig = indexConfig,
        rootPageId = metaPageData.tableCatalogRootPageId,
        onRootChanged = onTableCatalogRootChanged
    )
    private var indexCatalog = BTree(
        name = CatalogBoot.INDEX_CATALOG_INDEX_NAME,
        targetTable = CatalogBoot.INDEX_CATALOG_NAME,
        storageManager = storageManager,
        indexConfig = indexConfig,
        rootPageId = metaPageData.indexCatalogRootPageId,
        onRootChanged = onIndexCatalogRootChanged
    )
    private var columnCatalog = BTree(
        name = CatalogBoot.COLUMN_CATALOG_INDEX_NAME,
        targetTable = CatalogBoot.COLUMN_CATALOG_NAME,
        storageManager = storageManager,
        indexConfig = indexConfig,
        rootPageId = metaPageData.columnCatalogRootPageId,
        onRootChanged = onColumnCatalogRootChanged
    )

    fun resolveIndex(name: String): IndexRow?{
        val serialized = indexCatalogKeySerializer.serialize(listOf(name))
        val data = indexCatalog.search(serialized) ?: return null
        val deserialized = indexCatalogValueSerializer.deserialize(data).first
        return IndexRaw(deserialized).toRow()
    }

    fun resolveColumn(tableId: Long, ordinal: Int): ColumnRow?{
        val serialized = columnCatalogKeySerializer.serialize(listOf(tableId, ordinal))
        val data = columnCatalog.search(serialized) ?: return null
        val deserialized = columnCatalogValueSerializer.deserialize(data).first
        return ColumnRaw(deserialized).toRow()
    }

    fun resolveTable(name: String): TableRow?{
        val serialized = tableCatalogKeySerializer.serialize(listOf(name))
        val data = tableCatalog.search(serialized) ?: return null
        val deserialized = tableCatalogValueSerializer.deserialize(data).first
        return TableRaw(deserialized).toRow()
    }

    fun registerNewColumn(tableId: Long, ordinal: Int, name: String, type: String, nullable: Boolean): ColumnRow{
        val columnType = try {
            ColumnType.valueOf(type)
        } catch (e: IllegalArgumentException){
            throw CatalogException.CorruptedRow(
                SQLErrorDetail(
                    entityType = EntityType.CATALOG_ROW,
                    entityName = CatalogBoot.COLUMN_CATALOG_NAME
                ),
                e
            )
        }
        val keySerialized = columnCatalogKeySerializer.serialize(listOf(tableId, ordinal))
        val valueSerialized = columnCatalogValueSerializer.serialize(listOf(tableId, ordinal, name, type, nullable))
        columnCatalog.insert(keySerialized, valueSerialized)
        return ColumnRow(tableId, ordinal, name, columnType, nullable)
    }

    fun registerNewTable(tableId: Long, tableName: String, primaryIndexName: String?): TableRow{
        val keySerialized = tableCatalogKeySerializer.serialize(listOf(tableName))
        val valueSerialized = tableCatalogValueSerializer.serialize(listOf(tableId, tableName, primaryIndexName))
        tableCatalog.insert(keySerialized, valueSerialized)
        return TableRow(tableId, tableName, primaryIndexName)
    }

    fun registerNewIndex(
        indexId: Long,
        indexName: String,
        tableName: String,
        rootPageId: Long?,
        isPrimary: Boolean,
        isUnique: Boolean,
        keyColumns: List<IndexColumn>
    ): IndexRow{
        val keySerialized = indexCatalogKeySerializer.serialize(listOf(indexName))
        val valueSerialized = indexCatalogValueSerializer.serialize(listOf(indexId, indexName, tableName, rootPageId, isPrimary, isUnique, keyColumns.encodeKeyColumns()))
        indexCatalog.insert(keySerialized, valueSerialized)
        return IndexRow(indexId, indexName, tableName, rootPageId, isPrimary, isUnique, keyColumns)
    }

    fun updatePrimaryIndexName(tableName: String, primaryIndexName: String){
        val searchkey = tableCatalogKeySerializer.serialize(listOf(tableName))
        val value = tableCatalog.search(searchkey)
            ?: throw CatalogException.UndefinedTable(
                SQLErrorDetail(
                    entityType = EntityType.TABLE,
                    entityName = tableName
                )
            )
        val valueDeserialized = tableCatalogValueSerializer.deserialize(value).first
        val tableRow = TableRaw(valueDeserialized).toRow()
        val newTableRow = tableRow.copy(primaryIndexName = primaryIndexName)
        val newKeySerialized = tableCatalogKeySerializer.serialize(listOf(tableName))
        val newValueSerialized = tableCatalogValueSerializer.serialize(newTableRow.toList())
        tableCatalog.update(newKeySerialized, newKeySerialized , newValueSerialized)
    }

    fun updateIndexRootPageId(indexName: String, rootPageId: Long){
        val keySerialized = indexCatalogKeySerializer.serialize(listOf(indexName))

        val value = indexCatalog.search(keySerialized)
        requireOrThrow(value != null){ CatalogException.UndefinedObject(
            SQLErrorDetail(
                entityType = EntityType.INDEX,
                entityName = indexName
            )
        ) }

        val searchedValue = indexCatalogValueSerializer.deserialize(value).first
        val indexRow = IndexRaw(searchedValue).toRow()
        val newRow = indexRow.copy(rootPageId = rootPageId)
        val rowList = newRow.toList()
        val newKeySerialized = indexCatalogKeySerializer.serialize(listOf(indexName))
        val newValueSerialized = indexCatalogValueSerializer.serialize(rowList)

        indexCatalog.update(newKeySerialized, newKeySerialized, newValueSerialized)
    }

    fun getColumns(tableId: Long): List<ColumnRow>{
        return columnCatalog.traverse().filter {
            columnCatalogKeySerializer.deserialize(it.first)[0] == tableId
        }.map {
            val deserialized = columnCatalogValueSerializer.deserialize(it.second).first
            ColumnRaw(deserialized).toRow()
        }
    }

    fun getIndexes(tableName: String): List<IndexRow>{
        return indexCatalog.traverse().filter {
            val deserialized = indexCatalogValueSerializer.deserialize(it.second).first
            val row = IndexRaw(deserialized).toRow()
            row.tableName == tableName
        }.map {
            val deserialized = indexCatalogValueSerializer.deserialize(it.second).first
            IndexRaw(deserialized).toRow()
        }
    }
}
