package database

import exception.CatalogException
import config.SimpleConfig
import config.StorageConfig
import exception.DatabaseException
import exception.TableException
import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import schema.Row
import schema.RowColumn
import schema.RowSchema
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.uuid.Uuid

class DataBaseTest: BehaviorSpec({
    given("A database"){
        val dbPath = "test-database-${Uuid.random()}.db"
        val config = SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 100))
        val db = DataBase(config).apply { initialize() }
        afterSpec { db.close(); File(dbPath).delete() }

        val tableName = "test-table"
        val primaryIdxName = "test-table-primary-index"
        val primaryKeyName = "id"
        val columns = RowSchema(listOf(
            RowColumn(primaryKeyName, ColumnType.LONG, false, 0),
            RowColumn("column1", ColumnType.INT, true, null),
            RowColumn("column2", ColumnType.DOUBLE, true, null),
            RowColumn("column3", ColumnType.FLOAT, true, null)

        ))
        `when`("Load non-exist table"){
            then("Should throw an UndefinedTable"){}
            shouldThrow<CatalogException.UndefinedTable> {
                db.loadTable("non-exist-table")
            }
        }
        `when`("Create a table"){
            val primaryTable = db.createTable(
                tableName,
                primaryIdxName,
                columns
            )
            val row = Row(columns, listOf(1L, 10, 2.5, 3.5f))
            primaryTable.insertRow(row)

            then("loadTable should return a table backed by the same underlying data"){
                val loadedTable = db.loadTable(tableName)
                loadedTable.selectByKey(listOf(1L)).shouldNotBeNull {
                    this["column1"] shouldBe 10
                }
            }

            then("Creating primary index should throw an exception"){
                shouldThrow<DatabaseException.DuplicateObject> {
                    db.createIndex(
                        "temp1",
                        null,
                        tableName,
                        isPrimary = true,
                        isUnique = true,
                        keySchema = IndexKeySchema(listOf(
                            IndexColumn("id", ColumnType.LONG, false)
                        ))
                    )
                }
            }
            then("Creating primary index with non-existing table name should throw UndefinedTable Exception"){
                shouldThrow<CatalogException.UndefinedTable> {
                    db.createIndex(
                        "temp2",
                        null,
                        "non-existing table",
                        isPrimary = true,
                        isUnique = true,
                        keySchema = IndexKeySchema(listOf(
                            IndexColumn("id", ColumnType.LONG, false)
                        ))
                    )
                }
            }
        }
        val duplicatedColumns = RowSchema(listOf(
            RowColumn(primaryKeyName, ColumnType.LONG, false, 0),
            RowColumn("column1", ColumnType.INT, true, null),
            RowColumn("column2", ColumnType.DOUBLE, true, null),
            RowColumn("column3", ColumnType.FLOAT, true, null),
            RowColumn("column3", ColumnType.FLOAT, true, null)

        ))
        val tableNameNew = "test-table-2"
        val primaryIdxNameNew = "test-table-primary-index-3"
        `when`("Creating a table with duplicated columns"){
            then("DuplicateColumn should be thrown"){
                shouldThrow<DatabaseException.DuplicateColumn> {
                    db.createTable(
                        tableNameNew,
                        primaryIdxNameNew,
                        duplicatedColumns
                    )
                }
            }
        }

        val newTableName3 = "test-table-3"
        `when`("Creating a table with already existing primary index name"){
            then("DuplicateObject should be thrown"){
                shouldThrow<DatabaseException.DuplicateObject> {
                    db.createTable(
                        newTableName3,
                        primaryIdxName,
                        columns
                    )
                }
            }
        }

        val secondaryIndexName = "temp-idx"
        val indexSchema = IndexKeySchema(listOf(
            IndexColumn("column2", ColumnType.DOUBLE, true),
            IndexColumn("column3", ColumnType.FLOAT, true)
        ))
        `when`("Create secondary index with valid name, type"){
            val secondaryIndex = db.createIndex(
                secondaryIndexName,
                primaryIdxName,
                tableName,
                isPrimary = false,
                isUnique = false,
                keySchema = indexSchema
            )
            then("Secondary index should be created"){
                secondaryIndex.metadata.indexName shouldBe secondaryIndexName
                secondaryIndex.metadata.tableName shouldBe tableName
            }
        }
        val invalidSchema1 = IndexKeySchema(listOf(
            IndexColumn("column2", ColumnType.DOUBLE, true),
            IndexColumn("column4", ColumnType.FLOAT, true)
        ))
        `when`("Create secondary index with invalid name"){
            then("UndefinedColumn should be thrown"){
                shouldThrow<DatabaseException.UndefinedColumn> {
                    db.createIndex(
                        "invalidIndexName1",
                        primaryIdxName,
                        tableName,
                        isPrimary = false,
                        isUnique = false,
                        keySchema = invalidSchema1
                    )
                }
            }
        }
        val invalidSchema2 = IndexKeySchema(listOf(
            IndexColumn("column2", ColumnType.DOUBLE, true),
            IndexColumn("column3", ColumnType.STRING, true)
        ))
        `when`("Create secondary index with invalid type"){
            then("UndefinedColumn should be thrown"){
                shouldThrow<DatabaseException.UndefinedColumn> {
                    db.createIndex(
                        "invalidIndexName2",
                        primaryIdxName,
                        tableName,
                        isPrimary = false,
                        isUnique = false,
                        keySchema = invalidSchema2
                    )
                }
            }
        }

        `when`("Create secondary index with duplicated name"){
            then("DuplicateObject should be thrown"){
                shouldThrow<DatabaseException.DuplicateObject> {
                    db.createIndex(
                        secondaryIndexName,
                        primaryIdxName,
                        tableName,
                        isPrimary = false,
                        isUnique = false,
                        keySchema = indexSchema
                    )
                }
            }
        }

        val secondaryIndexName2 = "temp-idx-2"
        `when`("Create secondary index with non-exist primary index name"){
            then("UndefinedObject should be thrown"){
                shouldThrow<CatalogException.UndefinedObject> {
                    db.createIndex(
                        secondaryIndexName2,
                        "non-existing primary index name",
                        tableName,
                        isPrimary = false,
                        isUnique = false,
                        keySchema = indexSchema
                    )
                }
            }
        }
        `when`("Load index $secondaryIndexName"){
            then("Index should be returned"){
                val loadedIndex = db.loadIndex(secondaryIndexName)
                loadedIndex.metadata.indexName shouldBe secondaryIndexName
                loadedIndex.metadata.tableName shouldBe tableName
            }
        }

        `when`("Load non-exist index"){
            then("UndefinedObject should be thrown"){
                shouldThrow<CatalogException.UndefinedObject> {
                    db.loadIndex("non-existing index")
                }
            }
        }

        // Insert enough rows that the drop tests below actually walk a multi-level tree in
        // BTree.destroy(), not just a single leaf page - a small dataset would let a broken
        // destroy() (or a broken internal-node child lookup - see the InternalNode.rightMost-
        // ChildPageId regression this session already found from exactly this kind of gap) pass
        // silently.
        val bulkTable = db.loadTable(tableName)
        val bulkRowCount = 500
        for (id in 2L..bulkRowCount + 1L) {
            bulkTable.insertRow(Row(columns, listOf(id, id.toInt(), id.toDouble(), id.toFloat())))
        }

        // Issue #49: DROP INDEX (secondary only, standalone - table stays)
        `when`("Dropping a non-existent index"){
            then("UndefinedObject should be thrown"){
                shouldThrow<CatalogException.UndefinedObject> {
                    db.dropIndex("non-existing index")
                }
            }
        }

        `when`("Dropping the primary index directly"){
            then("DependentObjectsExist should be thrown - only DROP TABLE may remove it"){
                shouldThrow<DatabaseException.DependentObjectsExist> {
                    db.dropIndex(primaryIdxName)
                }
            }
            then("the primary index should be untouched - still loadable"){
                db.loadIndex(primaryIdxName).metadata.indexName shouldBe primaryIdxName
            }
        }

        `when`("Dropping the secondary index $secondaryIndexName"){
            db.dropIndex(secondaryIndexName)
            then("it should no longer be loadable"){
                shouldThrow<CatalogException.UndefinedObject> {
                    db.loadIndex(secondaryIndexName)
                }
            }
            then("the table itself and its primary index should be unaffected, including the bulk rows"){
                val reloaded = db.loadTable(tableName)
                reloaded.selectByKey(listOf(2L)).shouldNotBeNull { this["column1"] shouldBe 2 }
                reloaded.selectByKey(listOf(bulkRowCount + 1L)).shouldNotBeNull {
                    this["column1"] shouldBe bulkRowCount + 1
                }
            }
            then("the same index name can be registered again"){
                val recreated = db.createIndex(
                    secondaryIndexName,
                    primaryIdxName,
                    tableName,
                    isPrimary = false,
                    isUnique = false,
                    keySchema = indexSchema,
                )
                recreated.metadata.indexName shouldBe secondaryIndexName
            }
        }

        // Issue #50: DROP TABLE (table + every index it has, including primary)
        `when`("Dropping table $tableName"){
            db.dropTable(tableName)
            then("the table should no longer be loadable"){
                shouldThrow<CatalogException.UndefinedTable> {
                    db.loadTable(tableName)
                }
            }
            then("its primary index should no longer be loadable"){
                shouldThrow<CatalogException.UndefinedObject> {
                    db.loadIndex(primaryIdxName)
                }
            }
            then("the same table/primary-index names can be registered again, as a fresh table"){
                val recreatedTable = db.createTable(tableName, primaryIdxName, columns)
                val row = Row(columns, listOf(1L, 99, 1.5, 2.5f))
                recreatedTable.insertRow(row)
                db.loadTable(tableName).selectByKey(listOf(1L)).shouldNotBeNull {
                    this["column1"] shouldBe 99
                }
            }
        }
    }

    // Issue #51: ADD COLUMN / DROP COLUMN
    given("A table with bulk rows (spanning multiple leaves) and a secondary index"){
        val dbPath = "test-database-addcol-dropcol-${Uuid.random()}.db"
        val config = SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 100))
        val db = DataBase(config).apply { initialize() }
        afterSpec { db.close(); File(dbPath).delete() }

        val tableName = "ac-dc-table"
        val primaryIdxName = "ac-dc-primary"
        val columns = RowSchema(listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("score", ColumnType.INT, true, null),
        ))
        val table = db.createTable(tableName, primaryIdxName, columns)
        val rowCount = 500
        for (id in 1L..rowCount) {
            table.insertRow(Row(columns, listOf(id, id.toInt())))
        }
        val indexOnScore = "ac-dc-idx-score"
        db.createIndex(
            indexOnScore,
            primaryIdxName,
            tableName,
            isPrimary = false,
            isUnique = false,
            keySchema = IndexKeySchema(listOf(IndexColumn("score", ColumnType.INT, false))),
        )

        `when`("Dropping a column a secondary index references"){
            then("DependentObjectsExist should be thrown"){
                shouldThrow<DatabaseException.DependentObjectsExist> {
                    db.dropColumn(tableName, "score")
                }
            }
        }

        `when`("Dropping the primary key column"){
            then("DependentObjectsExist should be thrown"){
                shouldThrow<DatabaseException.DependentObjectsExist> {
                    db.dropColumn(tableName, "id")
                }
            }
        }

        `when`("Dropping a non-existent column"){
            then("UndefinedColumn should be thrown"){
                shouldThrow<DatabaseException.UndefinedColumn> {
                    db.dropColumn(tableName, "does-not-exist")
                }
            }
        }

        `when`("Adding a non-nullable column with no default value"){
            then("NotNullViolation should be thrown"){
                shouldThrow<DatabaseException.NotNullViolation> {
                    db.addColumn(tableName, "required", ColumnType.INT.name, nullable = false, defaultValue = null)
                }
            }
        }

        `when`("Adding a nullable column (no default)"){
            db.addColumn(tableName, "label", ColumnType.STRING.name, nullable = true, defaultValue = null)
            then("every one of the $rowCount rows (spanning multiple leaves) reads back correctly, new column null"){
                val reloaded = db.loadTable(tableName)
                for (id in 1L..rowCount) {
                    reloaded.selectByKey(listOf(id)).shouldNotBeNull {
                        this["score"] shouldBe id.toInt()
                        this["label"] shouldBe null
                    }
                }
            }
        }

        `when`("Adding a non-nullable column WITH a default value"){
            db.addColumn(tableName, "flag", ColumnType.BOOLEAN.name, nullable = false, defaultValue = true)
            then("every existing row gets backfilled with the default value"){
                val reloaded = db.loadTable(tableName)
                reloaded.selectByKey(listOf(1L)).shouldNotBeNull { this["flag"] shouldBe true }
                reloaded.selectByKey(listOf(rowCount.toLong())).shouldNotBeNull { this["flag"] shouldBe true }
            }
        }

        `when`("Adding a column whose name already exists"){
            then("DuplicateColumn should be thrown"){
                shouldThrow<DatabaseException.DuplicateColumn> {
                    db.addColumn(tableName, "score", ColumnType.INT.name, nullable = true, defaultValue = null)
                }
            }
        }

        `when`("Dropping the unreferenced 'label' column"){
            db.dropColumn(tableName, "label")
            then("every one of the $rowCount rows is still correct across every other column"){
                val reloaded = db.loadTable(tableName)
                for (id in 1L..rowCount) {
                    reloaded.selectByKey(listOf(id)).shouldNotBeNull {
                        this["score"] shouldBe id.toInt()
                        this["flag"] shouldBe true
                    }
                }
            }
            then("the freed ordinal can be reused by a new column without colliding"){
                db.addColumn(tableName, "note", ColumnType.STRING.name, nullable = true, defaultValue = null)
                db.loadTable(tableName).selectByKey(listOf(1L)).shouldNotBeNull { this["note"] shouldBe null }
            }
        }
    }

    // createIndex: a secondary index created on an already-populated table must be backfilled
    // with the existing rows, and (if isUnique) validated against them first - NULL-safely.
    given("A table with bulk rows, then a secondary index created afterward"){
        val dbPath = "test-database-index-backfill-${Uuid.random()}.db"
        val config = SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 100))
        val db = DataBase(config).apply { initialize() }
        afterSpec { db.close(); File(dbPath).delete() }

        val tableName = "backfill-table"
        val primaryIdxName = "backfill-primary"
        val columns = RowSchema(listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("email", ColumnType.STRING, true, null),
            RowColumn("nickname", ColumnType.STRING, true, null),
        ))
        val table = db.createTable(tableName, primaryIdxName, columns)
        val rowCount = 500
        for (id in 1L..rowCount) {
            // nickname is left NULL for every row here on purpose - used later to test that
            // multiple NULLs don't violate a UNIQUE index.
            table.insertRow(Row(columns, listOf(id, "user$id@example.com", null)))
        }

        `when`("creating a non-unique secondary index on 'email' after the data already exists"){
            val indexName = "backfill-idx-email"
            db.createIndex(
                indexName,
                primaryIdxName,
                tableName,
                isPrimary = false,
                isUnique = false,
                keySchema = IndexKeySchema(listOf(IndexColumn("email", ColumnType.STRING, false))),
            )
            then("every one of the $rowCount pre-existing rows (spanning multiple leaves) is findable through the new index"){
                val reloaded = db.loadTable(tableName)
                for (id in 1L..rowCount) {
                    reloaded.selectByIndex(indexName, listOf("user$id@example.com")).shouldNotBeNull {
                        this["id"] shouldBe id
                    }
                }
            }
        }

        `when`("creating a UNIQUE secondary index on a column with duplicate existing values"){
            table.insertRow(Row(columns, listOf(rowCount + 1L, "dup@example.com", null)))
            table.insertRow(Row(columns, listOf(rowCount + 2L, "dup@example.com", null)))
            then("UniqueViolation should be thrown, and no index/catalog row is left behind"){
                val dupIndexName = "backfill-idx-email-unique"
                shouldThrow<TableException.UniqueViolation> {
                    db.createIndex(
                        dupIndexName,
                        primaryIdxName,
                        tableName,
                        isPrimary = false,
                        isUnique = true,
                        keySchema = IndexKeySchema(listOf(IndexColumn("email", ColumnType.STRING, false))),
                    )
                }
                shouldThrow<CatalogException.UndefinedObject> {
                    db.loadIndex(dupIndexName)
                }
            }
        }

        `when`("creating a UNIQUE secondary index on a nullable column where every existing row is NULL"){
            then("index creation succeeds - NULL != NULL, so multiple NULLs don't violate uniqueness"){
                db.createIndex(
                    "backfill-idx-nickname-unique",
                    primaryIdxName,
                    tableName,
                    isPrimary = false,
                    isUnique = true,
                    keySchema = IndexKeySchema(listOf(IndexColumn("nickname", ColumnType.STRING, false))),
                )
            }
        }
    }
})
