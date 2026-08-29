import catalog.CatalogManager
import catalog.data.ColumnRow
import config.SimpleConfig
import exception.DatabaseException
import index.btree.BTree
import index.serializer.BinaryRowSerializer
import index.serializer.MultiColumnKeySerializer
import index.util.IndexKeySchema
import index.util.RowSchema
import storageEngine.BufferPoolManager
import storageEngine.MetaPageManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.StorageManager
import storageEngine.lru.FrameNodePolicy
import storageEngine.util.MetaPageOffset
import util.requireOrThrow
import kotlin.String


class DataBase(private val config: SimpleConfig) {
    private val diskManager = DiskManager(config.storageConfig, config.indexConfig)
    private val lruPolicy = FrameNodePolicy(config.storageConfig.midPointLruConfig)
    private val bufferPoolManager = BufferPoolManager(diskManager, lruPolicy, config.indexConfig, config.storageConfig.poolSize)
    private val metaPageManager = MetaPageManager(bufferPoolManager)
    private val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
    private val storageManager = StorageManager(freeSpaceManager, bufferPoolManager, config.indexConfig)
    private lateinit var catalogManager: CatalogManager

    fun initialize() {
        val metaPageData = metaPageManager.initialize()
        catalogManager = CatalogManager(
            metaPageData = metaPageData,
            storageManager = storageManager,
            indexConfig = config.indexConfig,
            onIndexCatalogRootChanged = { newRoot ->
                metaPageManager.updateRootPageId(MetaPageOffset.INDEX_CATALOG_ROOT_PAGE_ID, newRoot)
            },
            onTableCatalogRootChanged = { newRoot ->
                metaPageManager.updateRootPageId(MetaPageOffset.TABLE_CATALOG_ROOT_PAGE_ID, newRoot)
            },
            onColumnCatalogRootChanged = { newRoot ->
                metaPageManager.updateRootPageId(MetaPageOffset.COLUMN_CATALOG_ROOT_PAGE_ID, newRoot)
            }
        )
    }

    fun createIndex(
        indexName: String,
        tableId: Long,
        isPrimary: Boolean,
        targetTable: String,
        isUnique: Boolean,
        keySchema: IndexKeySchema,
        rowSchema: RowSchema,
    ): BTree<List<Any?>, List<Any?>> {
        val resolved = catalogManager.resolveIndex(indexName)
        requireOrThrow(resolved == null) { DatabaseException.IndexAlreadyExistsException(indexName) }
        val indexId = metaPageManager.getNextId(MetaPageOffset.NEXT_INDEX_ID)
        catalogManager.registerNewIndex(indexId, indexName, tableId, null, isPrimary, isUnique, keySchema.indexColumns)
        return BTree(
            indexName,
            targetTable,
            storageManager,
            MultiColumnKeySerializer(keySchema),
            BinaryRowSerializer(rowSchema),
            config.indexConfig,
-1L,
            onRootChanged = { newRoot ->
                catalogManager.updateIndexRootPageId(indexName, newRoot)
            }
        )
    }

    fun createTable(tableName: String, primaryIndexKeySchema: IndexKeySchema, columns: RowSchema){
        val resolved = catalogManager.resolveTable(tableName)
        requireOrThrow(resolved == null) { DatabaseException.TableAlreadyExistsException(tableName) }
        val tableId = metaPageManager.getNextId(MetaPageOffset.NEXT_TABLE_ID)
        val tableRow = catalogManager.registerNewTable(tableId, tableName, null)
        for((idx, column) in columns.rowColumns.withIndex()){
            createColumn(tableRow.tableId, idx, column.name, column.type.toString(), column.nullable)
        }
        createIndex(
            "${tableName}_PK_IDX",
            tableRow.tableId,
            true,
            tableRow.tableName,
            true,
            primaryIndexKeySchema,
            columns
        )
    }

    fun createColumn(tableId: Long, ordinal: Int, name: String, type: String, nullable: Boolean): ColumnRow{
        val resolved = catalogManager.resolveColumn(tableId, ordinal)
        requireOrThrow(resolved == null) { DatabaseException.ColumnAlreadyExistsException(tableId, name) }
        return catalogManager.registerNewColumn(tableId, ordinal, name, type, nullable)
    }
}