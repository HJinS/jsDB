package catalog

import catalog.exception.CatalogException
import config.MidpointLruConfig
import config.SimpleConfig
import config.StorageConfig
import index.util.ColumnType
import index.util.IndexColumn
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import storageEngine.BufferPoolManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.MetaPageManager
import storageEngine.StorageManager
import storageEngine.lru.FrameNodePolicy
import util.MetaPageOffset
import java.io.File
import java.util.UUID
import kotlin.random.Random


class CatalogManagerTest: BehaviorSpec({
    val dbPath = "test-catalog-manager-${UUID.randomUUID()}"
    val config = SimpleConfig(
        storageConfig=StorageConfig(
            dbPath = dbPath,
            poolSize = 100,
            midPointLruConfig = MidpointLruConfig(
                capacity = 100,
                lruOldBlocksTimeMs = 100
            )
        )
    )
    afterSpec { File(dbPath).delete() }
    given("A Catalog Manager") {
        val catalogManager = initCatalogManager(config)

        `when`("There is no table, index, column"){
            then("Resolve result should be null"){
                catalogManager.resolveTable("test_table") shouldBe null
                catalogManager.resolveIndex("test_index") shouldBe null
                catalogManager.resolveColumn(Random.nextLong(), 0) shouldBe null
            }
        }

        val tableName1 = "test_table1"
        val tableId1 = 0L
        `when`("Register new table $tableName1"){
            val tableRow = catalogManager.registerNewTable(tableId1, tableName1, null)
            then("Table row should be returned"){
                tableRow.tableName shouldBe tableName1
                tableRow.tableId shouldBe tableId1
                tableRow.primaryIndexName shouldBe null
            }
            then("The table row should be resolved with name"){
                val resolvedRow = catalogManager.resolveTable(tableName1)
                tableRow shouldBe resolvedRow
            }
        }

        val tableName2 = "test_table2"
        val tableId2 = 3L
        `when`("Register new table $tableName2"){
            val tableRow = catalogManager.registerNewTable(tableId2, tableName2, null)
            then("Table row should be returned"){
                tableRow.tableName shouldBe tableName2
                tableRow.tableId shouldBe tableId2
                tableRow.primaryIndexName shouldBe null
            }
            then("The table row should be resolved with name"){
                val resolvedRow = catalogManager.resolveTable(tableName2)
                tableRow shouldBe resolvedRow
            }
        }

        val columnName1 = "test_column1"
        val ordinal = 0
        val invalidColumnType = "INVALID_COLUMN_TYPE"
        val nullable = false
        `when`("Register new column with invalid column type $invalidColumnType"){
            then("CorruptedCatalogException should be thrown"){
                shouldThrow<CatalogException.CorruptedCatalogException> { catalogManager.registerNewColumn(
                    tableId1, ordinal, columnName1, invalidColumnType, nullable
                )}
            }
        }
        val validColumnType = ColumnType.LONG
        `when`("Register new column with valid column type ${validColumnType.name}"){
            val columnRow = catalogManager.registerNewColumn(tableId1, ordinal, columnName1, validColumnType.name, nullable)
            then("Column row should be returned"){
                columnRow.ordinal shouldBe ordinal
                columnRow.nullable shouldBe nullable
                columnRow.name shouldBe columnName1
                columnRow.type shouldBe validColumnType
                columnRow.tableId shouldBe tableId1
            }
            then("The column row should be resolved with name"){
                val resolvedRow = catalogManager.resolveColumn(tableId1, ordinal)
                columnRow shouldBe resolvedRow
            }
        }

        val columnName2 = "test_column2"
        val ordinal2 = 1
        val nullable2 = false
        val validColumnType2 = ColumnType.LONG
        `when`("Register new column with valid column type ${validColumnType2.name}"){
            val columnRow = catalogManager.registerNewColumn(tableId2, ordinal2, columnName2, validColumnType2.name, nullable2)
            then("Column row should be returned"){
                columnRow.ordinal shouldBe ordinal2
                columnRow.nullable shouldBe nullable2
                columnRow.name shouldBe columnName2
                columnRow.type shouldBe validColumnType2
                columnRow.tableId shouldBe tableId2
            }
            then("The column row should be resolved with name"){
                val resolvedRow = catalogManager.resolveColumn(tableId2, ordinal2)
                columnRow shouldBe resolvedRow
            }
        }


        val indexId = 0L
        val indexName = "test_index"
        val rootPageId = null
        val isPrimary = false
        val isUnique = false
        val keyColumns = listOf(
            IndexColumn("indexKey1", ColumnType.UUID, true, null, null),
            IndexColumn("indexKey2", ColumnType.LONG, true, null, null)
        )
        `when`("Register new index $indexName"){
            val indexRow = catalogManager.registerNewIndex(
                indexId,
                indexName,
                tableName1,
                rootPageId,
                isPrimary,
                isUnique,
                keyColumns
            )
            then("Index row should be returned"){
                indexRow.indexId shouldBe indexId
                indexRow.indexName shouldBe indexName
                indexRow.rootPageId shouldBe rootPageId
                indexRow.keyColumns shouldBeEqual keyColumns
                indexRow.isPrimary shouldBe isPrimary
                indexRow.isUnique shouldBe isUnique
            }
            then("The index row should be resolved with name"){
                val resolvedRow = catalogManager.resolveIndex(indexName)
                indexRow shouldBe resolvedRow
            }
        }
        val newRootPageId = 1L
        `when`("Update index root page id"){
            catalogManager.updateIndexRootPageId(indexName, newRootPageId)
            then("Resolved index root page id should be updated"){
                val newIndexRow = catalogManager.resolveIndex(indexName)
                newIndexRow shouldNotBeNull{ this.rootPageId shouldBe newRootPageId }
            }
        }
        val invalidTableName = "test_table_invalid"
        `when`("Update primary index name but there is no such table"){
            then("TableCatalogNotFound should be thrown"){
                shouldThrow<CatalogException.TableCatalogNotFound> { catalogManager.updatePrimaryIndexName(
                    invalidTableName, "new index name"
                ) }
            }
        }

        `when`("Update primary index name "){
            catalogManager.updatePrimaryIndexName(tableName1, indexName)
            then("Resolved table's primary index name should be updated"){
                val resolvedTableRowUpdated = catalogManager.resolveTable(tableName1)
                resolvedTableRowUpdated shouldNotBeNull { this.primaryIndexName shouldBe indexName }
            }
        }

        `when`("Get columns"){
            val columns = catalogManager.getColumns(tableId1)
            then("Table column should be returned"){
                columns.size shouldBe 1
                columns[0].tableId shouldBe tableId1
                columns[0].ordinal shouldBe ordinal
                columns[0].name shouldBe columnName1
                columns[0].type shouldBe validColumnType
            }
        }
    }

    given("encodeKeyColumns / decodeKeyColumns"){
        `when`("encoding and decoding a single IndexColumn"){
            val columns = listOf(IndexColumn("email", ColumnType.STRING, false))
            val decoded = columns.encodeKeyColumns().decodeKeyColumns()
            then("decoded result should equal the original"){
                decoded shouldBe columns
            }
        }

        `when`("encoding and decoding multiple IndexColumns (composite key)"){
            val columns = listOf(
                IndexColumn("lastName", ColumnType.STRING, false),
                IndexColumn("firstName", ColumnType.STRING, true),
                IndexColumn("id", ColumnType.LONG, false),
            )
            val decoded = columns.encodeKeyColumns().decodeKeyColumns()
            then("decoded result should equal the original list, in the same order"){
                decoded shouldBe columns
            }
        }

        `when`("encoding a column with localeTag and collationStrength set"){
            val columns = listOf(IndexColumn("name", ColumnType.STRING, false, "ko-KR", 2))
            val decoded = columns.encodeKeyColumns().decodeKeyColumns()
            then("localeTag and collationStrength should be preserved"){
                decoded shouldBe columns
                decoded[0].localeTag shouldBe "ko-KR"
                decoded[0].collationStrength shouldBe 2
            }
        }

        `when`("encoding a column without localeTag/collationStrength"){
            val columns = listOf(IndexColumn("id", ColumnType.LONG, false))
            val decoded = columns.encodeKeyColumns().decodeKeyColumns()
            then("localeTag and collationStrength should remain null"){
                decoded[0].localeTag shouldBe null
                decoded[0].collationStrength shouldBe null
            }
        }

        `when`("encoding an empty list of IndexColumns"){
            then("IndexKeyViolation should be thrown"){
                shouldThrow<CatalogException.IndexKeyViolation> { emptyList<IndexColumn>().encodeKeyColumns() }
            }
        }

        `when`("decoding a truncated (corrupted) byte array"){
            val columns = listOf(
                IndexColumn("lastName", ColumnType.STRING, false),
                IndexColumn("firstName", ColumnType.STRING, true),
            )
            val encoded = columns.encodeKeyColumns()
            val truncated = encoded.copyOfRange(0, encoded.size - 3)
            then("CorruptedCatalogException should be thrown"){
                shouldThrow<CatalogException.CorruptedCatalogException> { truncated.decodeKeyColumns() }
            }
        }

        `when`("decoding garbage bytes that don't represent a valid IndexColumn"){
            val garbage = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            then("CorruptedCatalogException should be thrown"){
                shouldThrow<CatalogException.CorruptedCatalogException> { garbage.decodeKeyColumns() }
            }
        }

        `when`("decoding bytes with a malformed VarInt length prefix"){
            // 첫 바이트(0x00)는 null bitmap: 5개 컬럼 전부 null이 아니라는 뜻 -> deserialize가 첫 필드(name, STRING)를 실제로 읽음.
            // name은 STRING이라 값을 읽기 전에 길이를 VarInt로 먼저 읽는데, 그 길이 자리에 0x80을 5번 연속 넣음.
            // VarInt는 각 바이트의 최상위 비트(MSB)가 1이면 "다음 바이트도 이어진다"는 신호인데,
            // 0x80(=1000_0000)은 하위 7비트가 전부 0이면서 MSB만 1이라 "값 없이 계속 이어지기만" 함.
            // 그래서 5바이트를 다 읽어도 안 끝나고 shift가 32를 넘어가서(끝나지 않는 VarInt에 대한 안전장치)
            // IndexException.VarIntTooLongException이 던져지고, 그게 CorruptedCatalogException으로 감싸지는지 확인.
            val malformed = byteArrayOf(0x00, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte())
            then("CorruptedCatalogException should be thrown"){
                shouldThrow<CatalogException.CorruptedCatalogException> { malformed.decodeKeyColumns() }
            }
        }
    }
}){
    companion object {
        fun initCatalogManager(config: SimpleConfig): CatalogManager{
            val diskManager = DiskManager(config.storageConfig, config.indexConfig)
            val lruPolicy = FrameNodePolicy(config.storageConfig.midPointLruConfig)
            val bufferPoolManager = BufferPoolManager(diskManager, lruPolicy, config.indexConfig, config.storageConfig.poolSize)
            val metaPageManager = MetaPageManager(bufferPoolManager)
            val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
            val storageManager = StorageManager(freeSpaceManager, bufferPoolManager, config.indexConfig)
            val metaPageData = metaPageManager.initialize()
            return CatalogManager(
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
    }
}