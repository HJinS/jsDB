package database

import catalog.CatalogManager
import config.SimpleConfig
import exception.CatalogException
import exception.DatabaseException
import index.btree.BTree
import index.serializer.BinaryRowSerializer
import index.serializer.MultiColumnKeySerializer
import kotlin.String
import schema.ColumnRow
import schema.IndexColumn
import schema.IndexHandle
import schema.IndexKeySchema
import schema.RowSchema
import schema.toPrimaryRowSchema
import schema.toRowColumn
import storageEngine.BufferPoolManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.MetaPageManager
import storageEngine.StorageManager
import storageEngine.lru.FrameNodePolicy
import util.EntityType
import util.INVALID_PAGE_ID
import util.MetaPageOffset
import util.PRIMARY_KEY_IDX_NAME_PREFIX
import util.SQLErrorDetail
import util.requireOrThrow

/**
 * Top-level façade wiring the storage engine (disk I/O, buffer pool, page/free-space management)
 * to the system catalog, and exposing table/index lifecycle operations. One instance per database
 * file.
 *
 * [initialize] must be called once before any other method — it opens or bootstraps the catalog
 * and wires each catalog BTree's root-changed callback back into the meta page.
 * */
class DataBase(private val config: SimpleConfig) {
    private val diskManager = DiskManager(config.storageConfig, config.indexConfig)
    private val lruPolicy = FrameNodePolicy(config.storageConfig.midPointLruConfig)
    private val bufferPoolManager =
        BufferPoolManager(diskManager, lruPolicy, config.indexConfig, config.storageConfig.poolSize)
    private val metaPageManager = MetaPageManager(bufferPoolManager)
    private val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
    private val storageManager =
        StorageManager(freeSpaceManager, bufferPoolManager, config.indexConfig)
    private lateinit var catalogManager: CatalogManager

    /**
     * Opens the database file (creating it if new) and loads or bootstraps the system catalog.
     * Must be called exactly once before any other method on this instance.
     * */
    fun initialize() {
        val metaPageData = metaPageManager.initialize()
        catalogManager =
            CatalogManager(
                metaPageData = metaPageData,
                storageManager = storageManager,
                indexConfig = config.indexConfig,
                onIndexCatalogRootChanged = { newRoot ->
                    metaPageManager.updateRootPageId(
                        MetaPageOffset.INDEX_CATALOG_ROOT_PAGE_ID,
                        newRoot,
                    )
                },
                onTableCatalogRootChanged = { newRoot ->
                    metaPageManager.updateRootPageId(
                        MetaPageOffset.TABLE_CATALOG_ROOT_PAGE_ID,
                        newRoot,
                    )
                },
                onColumnCatalogRootChanged = { newRoot ->
                    metaPageManager.updateRootPageId(
                        MetaPageOffset.COLUMN_CATALOG_ROOT_PAGE_ID,
                        newRoot,
                    )
                },
            )
    }

    /**
     * Closes the underlying file handle.
     *
     * Does **not** flush the buffer pool first — [storageEngine.BufferPoolManager] has no
     * "flush all" operation, so any dirty page not already flushed individually may not reach
     * disk before the file closes. Whether a subsequent re-open preserves all writes made before
     * this call is not yet verified. See issue #47.
     * */
    fun close() {
        diskManager.close()
    }

    /**
     * Registers and creates a new index (BTree) for [tableName], after validating that
     * [indexName] is free, the table exists, and every column in [keySchema] already exists on
     * the table with a matching type. For a primary index ([isPrimary] true), also requires the
     * table not already have one.
     *
     * @param primaryIdxName Only meaningful when building a **secondary** index ([isPrimary]
     *   false): the table's primary index name, needed to resolve the value schema stored
     *   alongside the primary key. Ignored when [isPrimary] is true.
     * @return A ready-to-use [IndexHandle] (the [BTree] plus its key/value serializers).
     * */
    fun createIndex(
        indexName: String,
        primaryIdxName: String?,
        tableName: String,
        isPrimary: Boolean,
        isUnique: Boolean,
        keySchema: IndexKeySchema,
    ): IndexHandle {
        val resolved = catalogManager.resolveIndex(indexName)
        requireOrThrow(resolved == null) {
            DatabaseException.DuplicateObject(
                SQLErrorDetail(entityType = EntityType.INDEX, entityName = indexName)
            )
        }
        val tableData = catalogManager.resolveTable(tableName)
        requireOrThrow(tableData != null) {
            CatalogException.UndefinedTable(
                SQLErrorDetail(entityType = EntityType.TABLE, entityName = tableName)
            )
        }
        if (isPrimary) {
            requireOrThrow(tableData.primaryIndexName == null) {
                DatabaseException.DuplicateObject(
                    SQLErrorDetail(entityType = EntityType.PRIMARY_INDEX, tableName = tableName)
                )
            }
        }

        val tableColumns = catalogManager.getColumns(tableData.tableId).associateBy { it.name }

        val invalidColumns =
            keySchema.indexColumns.filter { col ->
                val tableColumn = tableColumns[col.name]
                tableColumn == null || tableColumn.type != col.type
            }

        requireOrThrow(invalidColumns.isEmpty()) {
            DatabaseException.UndefinedColumn(
                SQLErrorDetail(
                    entityType = EntityType.INDEX_KEY_COLUMN,
                    entityName = indexName,
                    tableName = tableName,
                    columnNames = invalidColumns.map { it.name },
                )
            )
        }

        val indexId = metaPageManager.getNextId(MetaPageOffset.NEXT_INDEX_ID)
        val valueSchema = resolveIndexValueSchema(primaryIdxName, tableName, isPrimary)
        val indexData =
            catalogManager.registerNewIndex(
                indexId,
                indexName,
                tableName,
                null,
                isPrimary,
                isUnique,
                keySchema.indexColumns,
            )
        val index =
            BTree(
                indexName,
                tableName,
                storageManager,
                config.indexConfig,
                INVALID_PAGE_ID,
                onRootChanged = { newRoot ->
                    catalogManager.updateIndexRootPageId(indexName, newRoot)
                },
            )
        return IndexHandle(
            indexData,
            index,
            MultiColumnKeySerializer(keySchema),
            BinaryRowSerializer(valueSchema),
        )
    }

    /** Loads an existing index by name from the catalog into a ready-to-use [IndexHandle]. */
    fun loadIndex(indexName: String): IndexHandle {
        val indexData = catalogManager.resolveIndex(indexName)
        requireOrThrow(indexData != null) {
            CatalogException.UndefinedObject(
                SQLErrorDetail(entityType = EntityType.INDEX, entityName = indexName)
            )
        }
        val targetTableData = catalogManager.resolveTable(indexData.tableName)
        requireOrThrow(targetTableData != null) {
            CatalogException.UndefinedTable(
                SQLErrorDetail(entityType = EntityType.TABLE, entityName = indexData.tableName)
            )
        }
        val valueSchema =
            resolveIndexValueSchema(
                targetTableData.primaryIndexName,
                indexData.tableName,
                indexName == targetTableData.primaryIndexName,
            )
        val index =
            BTree(
                indexName,
                indexData.tableName,
                storageManager,
                config.indexConfig,
                indexData.rootPageId ?: INVALID_PAGE_ID,
                onRootChanged = { newRoot ->
                    catalogManager.updateIndexRootPageId(indexName, newRoot)
                },
            )
        return IndexHandle(
            indexData,
            index,
            MultiColumnKeySerializer(IndexKeySchema(indexData.keyColumns)),
            BinaryRowSerializer(valueSchema),
        )
    }

    /**
     * Creates a new table: registers it and its columns in the catalog, then builds its primary
     * index from the columns marked with a non-null `primaryKeyOrder`.
     *
     * @param primaryIdxName Name for the primary index; defaults to
     *   [PRIMARY_KEY_IDX_NAME_PREFIX] + [tableName] when null.
     * @param columns The table's full column list. Exactly the ones with a non-null
     *   `primaryKeyOrder` become the primary key, ordered by that value; none of them may be
     *   nullable.
     * */
    fun createTable(tableName: String, primaryIdxName: String?, columns: RowSchema): Table {
        val resolved = catalogManager.resolveTable(tableName)
        requireOrThrow(resolved == null) {
            DatabaseException.DuplicateTable(
                SQLErrorDetail(entityType = EntityType.TABLE, entityName = tableName)
            )
        }
        val duplicateNames =
            columns.rowColumns
                .groupingBy { it.name }
                .eachCount()
                .filter { it.value > 1 }
                .keys
                .toList()
        requireOrThrow(duplicateNames.isEmpty()) {
            DatabaseException.DuplicateColumn(
                SQLErrorDetail(
                    entityType = EntityType.COLUMN,
                    tableName = tableName,
                    columnNames = duplicateNames,
                )
            )
        }
        val tableId = metaPageManager.getNextId(MetaPageOffset.NEXT_TABLE_ID)
        val tableRow = catalogManager.registerNewTable(tableId, tableName, null)
        var primaryKeyColumns = columns.rowColumns.filter { it.primaryKeyOrder != null }
        val nullablePkColumns = primaryKeyColumns.filter { it.nullable }
        requireOrThrow(nullablePkColumns.isEmpty()) {
            DatabaseException.NotNullViolation(
                SQLErrorDetail(
                    entityType = EntityType.PRIMARY_KEY,
                    tableName = tableName,
                    columnNames = nullablePkColumns.map { it.name },
                )
            )
        }
        for ((idx, column) in columns.rowColumns.withIndex()) {
            createColumn(
                tableRow.tableId,
                idx,
                column.name,
                column.type.toString(),
                column.nullable,
            )
        }
        primaryKeyColumns = primaryKeyColumns.sortedBy { it.primaryKeyOrder }
        val primaryIndexKeySchema =
            IndexKeySchema(
                primaryKeyColumns.map { IndexColumn(it.name, it.type, descending = false) }
            )

        val primaryIdxName = primaryIdxName ?: PRIMARY_KEY_IDX_NAME_PREFIX.format(tableName)
        val primaryIndex =
            createIndex(
                primaryIdxName,
                null,
                tableRow.tableName,
                isPrimary = true,
                isUnique = true,
                keySchema = primaryIndexKeySchema,
            )
        val rowSchema = resolveIndexValueSchema(tableRow.primaryIndexName, tableName, true)
        catalogManager.updatePrimaryIndexName(tableName, primaryIdxName)
        return Table(
            rowSchema,
            primaryIndex,
            emptyMap(),
        )
    }

    /** Loads an existing table by name: its primary index plus every non-primary index registered against it. */
    fun loadTable(tableName: String): Table {
        val tableData =
            catalogManager.resolveTable(tableName)
                ?: throw CatalogException.UndefinedTable(
                    SQLErrorDetail(entityType = EntityType.TABLE, entityName = tableName)
                )
        val primaryIdxName =
            tableData.primaryIndexName
                ?: throw DatabaseException.UndefinedObject(
                    SQLErrorDetail(entityType = EntityType.PRIMARY_INDEX, tableName = tableName)
                )
        val primaryIdxHandle = loadIndex(primaryIdxName)
        val rowSchema = resolveIndexValueSchema(tableData.primaryIndexName, tableName, true)
        val secondaryIndexes =
            catalogManager
                .getIndexes(tableName)
                .filter { !it.isPrimary }
                .map { it.indexName }
                .associateWith { loadIndex(it) }

        return Table(
            rowSchema,
            primaryIdxHandle,
            secondaryIndexes,
        )
    }

    /** Registers one column of an existing table in the catalog. */
    fun createColumn(
        tableId: Long,
        ordinal: Int,
        name: String,
        type: String,
        nullable: Boolean,
    ): ColumnRow {
        val resolved = catalogManager.resolveColumn(tableId, ordinal)
        requireOrThrow(resolved == null) {
            DatabaseException.DuplicateColumn(
                SQLErrorDetail(
                    entityType = EntityType.COLUMN,
                    entityName = name,
                    tableName = "tableId=$tableId",
                )
            )
        }
        return catalogManager.registerNewColumn(tableId, ordinal, name, type, nullable)
    }

    /**
     * Builds the [RowSchema] an index's BTree stores as its *value*, which differs by [isPrimary]:
     * - primary index: the table's full row. This is an index-organized table — the primary
     *   index's leaves *are* the row storage, so its value must hold every column.
     * - secondary index: just the primary key columns, resolved from [primaryIdxName]. A
     *   secondary index only points back to the row via the primary key; `Table` re-fetches the
     *   full row through the primary index using that key.
     * */
    private fun resolveIndexValueSchema(
        primaryIdxName: String?,
        tableName: String,
        isPrimary: Boolean,
    ): RowSchema {
        return if (isPrimary) {
            val tableData = catalogManager.resolveTable(tableName)
            requireOrThrow(tableData != null) {
                CatalogException.UndefinedTable(
                    SQLErrorDetail(entityType = EntityType.TABLE, entityName = tableName)
                )
            }
            val columns = catalogManager.getColumns(tableData.tableId)
            requireOrThrow(columns.isNotEmpty()) {
                CatalogException.InvalidDefinition(
                    SQLErrorDetail(
                        entityType = EntityType.TABLE,
                        entityName = tableName,
                        reason = "has no columns",
                    )
                )
            }
            RowSchema(columns.sortedBy { it.ordinal }.map { it.toRowColumn() })
        } else {
            val primaryIdxName = primaryIdxName ?: PRIMARY_KEY_IDX_NAME_PREFIX.format(tableName)
            val primaryIndexRow =
                catalogManager.resolveIndex(primaryIdxName)
                    ?: throw CatalogException.UndefinedObject(
                        SQLErrorDetail(entityType = EntityType.INDEX, entityName = primaryIdxName)
                    )
            IndexKeySchema(primaryIndexRow.keyColumns).toPrimaryRowSchema()
        }
    }
}
