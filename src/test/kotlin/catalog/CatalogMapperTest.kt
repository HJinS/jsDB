package catalog

import catalog.data.ColumnRaw
import catalog.data.IndexRaw
import schema.IndexRow
import catalog.data.TableRaw
import schema.TableRow
import exception.CatalogException
import schema.ColumnType
import schema.IndexColumn
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import util.SqlState

class CatalogMapperTest: BehaviorSpec({
    given("An indexRow"){
        val indexId = 0L
        val indexName = "test index"
        val tableName = "test table"
        val rootPageId = null
        val isPrimary = false
        val isUnique = true
        val keyColumns = listOf(
            IndexColumn(
                "testColumn1",
                ColumnType.INT,
                true
            ),
            IndexColumn(
                "testColumn2",
                ColumnType.INT,
                false
            ))
        val indexRow = IndexRow(
            indexId,
            indexName,
            tableName,
            rootPageId,
            isPrimary,
            isUnique,
            keyColumns
        )
        val convertedRaw = indexRow.toList()
        `when`("Convert indexRow to list"){
            then("Listed rows should be returned"){
                convertedRaw[0] shouldBe indexId
                convertedRaw[1] shouldBe indexName
                convertedRaw[2] shouldBe tableName
                convertedRaw[3] shouldBe rootPageId
                convertedRaw[4] shouldBe isPrimary
                convertedRaw[5] shouldBe isUnique
                convertedRaw[6] shouldBe keyColumns.encodeKeyColumns()
            }
        }
        `when`("Convert convertedRaw to IndexRow again"){
            val reconvertedRow = IndexRaw(convertedRaw).toRow()
            then("ReconvertedRow should be indexRow"){
                indexRow shouldBe reconvertedRow
            }
        }
        val invalidRaw = listOf(
            convertedRaw[0],
            1L,
            convertedRaw[2],
            convertedRaw[3],
            convertedRaw[4],
            convertedRaw[5],
            convertedRaw[6]
        )
        `when`("Convert invalid list to indexRow(invalid type)"){
            then("CorruptedRow should be thrown"){
                val e= shouldThrow<CatalogException.CorruptedRow> { IndexRaw(invalidRaw).toRow() }
                e.message shouldBe "[${SqlState.INTERNAL_ERROR.code}] Catalog row '${CatalogBoot.INDEX_CATALOG_NAME}'"
            }
        }
        val invalidRaw2 = listOf(
            convertedRaw[0],
            convertedRaw[1],
            convertedRaw[2],
            convertedRaw[3],
            convertedRaw[4],
            convertedRaw[5]
        )
        `when`("Convert invalid list to indexRow(out of bound)"){
            then("CorruptedRow should be thrown"){
                val e = shouldThrow<CatalogException.CorruptedRow> { IndexRaw(invalidRaw2).toRow() }
                e.message shouldBe "[${SqlState.INTERNAL_ERROR.code}] Catalog row '${CatalogBoot.INDEX_CATALOG_NAME}'"
            }
        }
    }
    given("An tableRow"){
        val tableId = 0L
        val tableName = "test table"
        val primaryIndexName = null
        val tableRow = TableRow(tableId, tableName, primaryIndexName)
        val convertedRaw = tableRow.toList()
        `when`("Convert tableRow to list"){
            then("Listed rows should be returned"){
                convertedRaw[0] shouldBe tableId
                convertedRaw[1] shouldBe tableName
                convertedRaw[2] shouldBe primaryIndexName
            }
        }
        `when`("Convert convertedRaw to TableRaw again"){
            val reconvertedRow = TableRaw(convertedRaw).toRow()
            then("ReconvertedRow should be indexRow"){
                tableRow shouldBe reconvertedRow
            }
        }
        val invalidRaw = listOf(
            convertedRaw[0],
            1L,
            convertedRaw[2]
        )
        `when`("Convert invalid list to tableRow(invalid type)"){
            then("CorruptedRow should be thrown"){
                val e = shouldThrow<CatalogException.CorruptedRow> { TableRaw(invalidRaw).toRow() }
                e.message shouldBe "[${SqlState.INTERNAL_ERROR.code}] Catalog row '${CatalogBoot.TABLE_CATALOG_NAME}'"
            }
        }
        val invalidRaw2 = listOf(
            convertedRaw[0],
            convertedRaw[1]
        )
        `when`("Convert invalid list to tableRow(out of bound)"){
            then("CorruptedRow should be thrown"){
                val e = shouldThrow<CatalogException.CorruptedRow> { TableRaw(invalidRaw2).toRow() }
                e.message shouldBe "[${SqlState.INTERNAL_ERROR.code}] Catalog row '${CatalogBoot.TABLE_CATALOG_NAME}'"
            }
        }
    }

    given("An ColumnRaw"){
        val tableId = 1L
        val ordinal = 0
        val name = "test column"
        val type = ColumnType.STRING.name
        val nullable = false

        val columnRaw = ColumnRaw(listOf(
            tableId, ordinal, name, type, nullable
        ))
        `when`("Convert columnRaw to columnRow again"){
            val convertedRow = columnRaw.toRow()
            then("ReconvertedRow should be indexRow"){
                convertedRow.tableId shouldBe tableId
                convertedRow.ordinal shouldBe ordinal
                convertedRow.name shouldBe name
                convertedRow.type.name shouldBe type
                convertedRow.nullable shouldBe nullable
            }
        }
        val invalidRaw = listOf(
            tableId,
            1L,
            name
        )
        `when`("Convert invalid list to columnRow(invalid type)"){
            then("CorruptedRow should be thrown"){
                val e = shouldThrow<CatalogException.CorruptedRow> { ColumnRaw(invalidRaw).toRow() }
                e.message shouldBe "[${SqlState.INTERNAL_ERROR.code}] Catalog row '${CatalogBoot.COLUMN_CATALOG_NAME}'"
            }
        }
        val invalidRaw2 = listOf(
            tableId,
            ordinal
        )
        `when`("Convert invalid list to columnRow(out of bound)"){
            then("CorruptedRow should be thrown"){
                val e = shouldThrow<CatalogException.CorruptedRow> { ColumnRaw(invalidRaw2).toRow() }
                e.message shouldBe "[${SqlState.INTERNAL_ERROR.code}] Catalog row '${CatalogBoot.COLUMN_CATALOG_NAME}'"
            }
        }

        val invalidRaw3 = listOf(
            tableId, ordinal, name, "invalid type", nullable
        )
        `when`("Convert invalid list to columnRow(invalid column type)"){
            then("CorruptedRow should be thrown"){
                val e = shouldThrow<CatalogException.CorruptedRow> { ColumnRaw(invalidRaw3).toRow() }
                e.message shouldBe "[${SqlState.INTERNAL_ERROR.code}] Catalog row '${CatalogBoot.COLUMN_CATALOG_NAME}'"
            }
        }
    }
})