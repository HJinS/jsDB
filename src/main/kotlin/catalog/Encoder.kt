package catalog

import catalog.exception.CatalogException
import index.serializer.BinaryRowSerializer
import index.util.ColumnType
import index.util.IndexColumn

private val indexColumnSerializer = BinaryRowSerializer(CatalogBoot.INDEX_COLUMN_ROW)

fun List<IndexColumn>.encodeKeyColumns(): ByteArray {
    if (this.isEmpty()) throw CatalogException.IndexKeyViolation()
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
        val decodeTarget = ByteArray(this.size - offset)
        System.arraycopy(this, offset, decodeTarget, 0, this.size - offset)
        val (raw, consumed) = indexColumnSerializer.deserialize(decodeTarget)
        offset += consumed
        try{
            result += IndexColumn(
                name = raw[0] as String,
                type = ColumnType.valueOf(raw[1] as String),
                descending = raw[2] as Boolean,
                localeTag = raw[3] as String?,
                collationStrength = raw[4] as Int?,
            )
        } catch (e: IndexOutOfBoundsException){
            throw CatalogException.CorruptedCatalogException(CatalogBoot.COLUMN_CATALOG_NAME, e)
        } catch (e: ClassCastException){
            throw CatalogException.CorruptedCatalogException(CatalogBoot.COLUMN_CATALOG_NAME, e)
        } catch (e: IllegalArgumentException){
            throw CatalogException.CorruptedCatalogException(CatalogBoot.COLUMN_CATALOG_NAME, e)
        }
    }
    return result
}