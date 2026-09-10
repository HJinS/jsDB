package catalog

import exception.CatalogException
import exception.IndexException
import index.serializer.BinaryRowSerializer
import schema.ColumnType
import schema.IndexColumn
import util.EntityType
import util.SQLErrorDetail

private val indexColumnSerializer = BinaryRowSerializer(CatalogBoot.INDEX_COLUMN_ROW)

fun List<IndexColumn>.encodeKeyColumns(): ByteArray {
    if (this.isEmpty()) throw CatalogException.InvalidDefinition(
        SQLErrorDetail(entityType = EntityType.INDEX, reason = "must have at least one key column")
    )
    val rows = this.map { column ->
        indexColumnSerializer.serialize(listOf(
            column.name, column.type.name, column.descending, column.localeTag, column.collationStrength
        ))
    }
    return rows.reduce(ByteArray::plus)
}

fun ByteArray.decodeKeyColumns(): List<IndexColumn> {
    var offset = 0
    val result = mutableListOf<IndexColumn>()
    while (offset < this.size) {
        try{
            val decodeTarget = ByteArray(this.size - offset)
            System.arraycopy(this, offset, decodeTarget, 0, this.size - offset)
            val (raw, consumed) = indexColumnSerializer.deserialize(decodeTarget)
            offset += consumed
            result += IndexColumn(
                name = raw[0] as String,
                type = ColumnType.valueOf(raw[1] as String),
                descending = raw[2] as Boolean,
                localeTag = raw[3] as String?,
                collationStrength = raw[4] as Int?,
            )
        } catch (e: IndexOutOfBoundsException){
            throw CatalogException.CorruptedRow(
                SQLErrorDetail(
                    entityType = EntityType.CATALOG_ROW,
                    entityName = CatalogBoot.COLUMN_CATALOG_NAME
                ),
                e
            )
        } catch (e: ClassCastException){
            throw CatalogException.CorruptedRow(
                SQLErrorDetail(
                    entityType = EntityType.CATALOG_ROW,
                    entityName = CatalogBoot.COLUMN_CATALOG_NAME
                ),
                e
            )
        } catch (e: IllegalArgumentException){
            throw CatalogException.CorruptedRow(
                SQLErrorDetail(
                    entityType = EntityType.CATALOG_ROW,
                    entityName = CatalogBoot.COLUMN_CATALOG_NAME
                ),
                e
            )
        } catch (e: IndexException){
            throw CatalogException.CorruptedRow(
                SQLErrorDetail(
                    entityType = EntityType.CATALOG_ROW,
                    entityName = CatalogBoot.COLUMN_CATALOG_NAME
                ),
                e
            )
        }
    }
    return result
}