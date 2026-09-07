import catalog.CatalogManager
import catalog.data.ColumnRow
import catalog.exception.CatalogException
import config.SimpleConfig
import exception.DatabaseException
import index.btree.BTree
import index.serializer.BinaryRowSerializer
import index.serializer.MultiColumnKeySerializer
import index.util.IndexColumn
import index.util.IndexKeySchema
import index.util.RowSchema
import index.util.toPrimaryRowSchema
import index.util.toRowColumn
import storageEngine.BufferPoolManager
import storageEngine.MetaPageManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.StorageManager
import storageEngine.lru.FrameNodePolicy
import util.INVALID_PAGE_ID
import util.MetaPageOffset
import util.PRIMARY_KEY_IDX_NAME_PREFIX
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

    fun close(){
        diskManager.close()
    }

    fun createIndex(
        indexName: String,
        primaryIdxName: String?,
        tableName: String,
        isPrimary: Boolean,
        isUnique: Boolean,
        keySchema: IndexKeySchema,
    ): BTree<List<Any?>, List<Any?>> {
        val resolved = catalogManager.resolveIndex(indexName)
        requireOrThrow(resolved == null) { DatabaseException.IndexAlreadyExistsException(indexName) }
        val tableData = catalogManager.resolveTable(tableName)
        requireOrThrow(tableData != null) { CatalogException.TableCatalogNotFound(tableName) }
        if (isPrimary) {
            requireOrThrow(tableData.primaryIndexName == null) {
                DatabaseException.PrimaryIndexAlreadyExistsException(tableName)
            }
        }

        val tableColumns = catalogManager.getColumns(tableData.tableId).associateBy { it.name }

        val invalidColumns = keySchema.indexColumns.filter { col ->
            val tableColumn = tableColumns[col.name]
            tableColumn == null || tableColumn.type != col.type
        }

        requireOrThrow(invalidColumns.isEmpty()) { DatabaseException.UnknownKeyColumnException(indexName, tableName, invalidColumns.map { it.name }) }

        val indexId = metaPageManager.getNextId(MetaPageOffset.NEXT_INDEX_ID)
        val valueSchema = resolveIndexValueSchema(primaryIdxName, tableName, isPrimary)
        catalogManager.registerNewIndex(indexId, indexName, tableName, null, isPrimary, isUnique, keySchema.indexColumns)
        return BTree(
            indexName,
            tableName,
            storageManager,
            MultiColumnKeySerializer(keySchema),
            BinaryRowSerializer(valueSchema),
            config.indexConfig,
            INVALID_PAGE_ID,
            onRootChanged = { newRoot ->
                catalogManager.updateIndexRootPageId(indexName, newRoot)
            }
        )
    }

    fun loadIndex(indexName: String): BTree<List<Any?>, List<Any?>> {
        val indexData = catalogManager.resolveIndex(indexName)
        requireOrThrow(indexData != null) { CatalogException.IndexNotFound(indexName) }
        val targetTableData = catalogManager.resolveTable(indexData.tableName)
        requireOrThrow(targetTableData != null){ CatalogException.TableCatalogNotFound(indexData.tableName) }
        val valueSchema = resolveIndexValueSchema(
            targetTableData.primaryIndexName,
            indexData.tableName,
            indexName == targetTableData.primaryIndexName,
        )
        return BTree(
            indexName,
            indexData.tableName,
            storageManager,
            MultiColumnKeySerializer(IndexKeySchema(indexData.keyColumns)),
            BinaryRowSerializer(valueSchema),
            config.indexConfig,
            INVALID_PAGE_ID,
            onRootChanged = { newRoot ->
                catalogManager.updateIndexRootPageId(indexName, newRoot)
            }
        )
    }

    fun createTable(tableName: String, primaryIdxName: String?, columns: RowSchema): BTree<List<Any?>, List<Any?>>{
        val resolved = catalogManager.resolveTable(tableName)
        requireOrThrow(resolved == null) { DatabaseException.TableAlreadyExistsException(tableName) }
        val duplicateNames = columns.rowColumns
            .groupingBy { it.name }
            .eachCount()
            .filter { it.value > 1 }
            .keys.toList()
        requireOrThrow(duplicateNames.isEmpty()) { DatabaseException.DuplicateColumnNameException(tableName, duplicateNames) }
        val tableId = metaPageManager.getNextId(MetaPageOffset.NEXT_TABLE_ID)
        val tableRow = catalogManager.registerNewTable(tableId, tableName, null)
        var primaryKeyColumns = columns.rowColumns.filter { it.primaryKeyOrder != null }
        val nullablePkColumns = primaryKeyColumns.filter { it.nullable }
        requireOrThrow(nullablePkColumns.isEmpty()) {
            DatabaseException.PrimaryKeyMustNotBeNullableException(tableName, nullablePkColumns.map{ it.name })
        }
        for((idx, column) in columns.rowColumns.withIndex()){
            createColumn(tableRow.tableId, idx, column.name, column.type.toString(), column.nullable)
        }
        primaryKeyColumns = primaryKeyColumns.sortedBy { it.primaryKeyOrder }
        val primaryIndexKeySchema = IndexKeySchema(
            primaryKeyColumns.map { IndexColumn(it.name, it.type, descending = false) }
        )

        val primaryIdxName = primaryIdxName ?: PRIMARY_KEY_IDX_NAME_PREFIX.format(tableName)
        val primaryIndex = createIndex(
            primaryIdxName,
            null,
            tableRow.tableName,
            isPrimary = true,
            isUnique = true,
            keySchema = primaryIndexKeySchema
        )
        catalogManager.updatePrimaryIndexName(tableName, primaryIdxName)
        return primaryIndex
    }

    fun loadTable(tableName: String): BTree<List<Any?>, List<Any?>> {
        val tableData = catalogManager.resolveTable(tableName) ?: throw CatalogException.TableCatalogNotFound(tableName)
        val primaryIdxName = tableData.primaryIndexName ?: throw DatabaseException.PrimaryIndexNotYetCreatedException(tableName)
        return loadIndex(primaryIdxName)
    }

    fun createColumn(tableId: Long, ordinal: Int, name: String, type: String, nullable: Boolean): ColumnRow{
        val resolved = catalogManager.resolveColumn(tableId, ordinal)
        requireOrThrow(resolved == null) { DatabaseException.ColumnAlreadyExistsException(tableId, name) }
        return catalogManager.registerNewColumn(tableId, ordinal, name, type, nullable)
    }

    private fun resolveIndexValueSchema(
        primaryIdxName: String?,
        tableName: String,
        isPrimary: Boolean
    ): RowSchema{
        return if (isPrimary) {
            val tableData = catalogManager.resolveTable(tableName)
            requireOrThrow(tableData != null) { CatalogException.TableCatalogNotFound(tableName) }
            val columns = catalogManager.getColumns(tableData.tableId)
            requireOrThrow(columns.isNotEmpty()) { CatalogException.TableRowEmpty(tableName) }
            RowSchema(columns.sortedBy { it.ordinal }.map { it.toRowColumn()})
        } else {
            val primaryIdxName = primaryIdxName ?: PRIMARY_KEY_IDX_NAME_PREFIX.format(tableName)
            val primaryIndexRow = catalogManager.resolveIndex(primaryIdxName)
                ?: throw CatalogException.IndexNotFound(primaryIdxName)
            IndexKeySchema(primaryIndexRow.keyColumns).toPrimaryRowSchema()
        }
    }
}