package database

import exception.CatalogException
import config.SimpleConfig
import config.StorageConfig
import exception.DatabaseException
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
import java.util.UUID

class DataBaseTest: BehaviorSpec({
    given("A database"){
        val dbPath = "test-database-${UUID.randomUUID()}.db"
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
    }
})