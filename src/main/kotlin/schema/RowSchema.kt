package schema

import exception.DatabaseException
import util.EntityType
import util.SQLErrorDetail
import util.requireOrThrow

data class RowSchema(
    val rowColumns: List<RowColumn>
){
    fun columnIndex(name: String): Int =
        rowColumns.indexOfFirst {
            it.name == name
        }.also {
            requireOrThrow(it >= 0) {
                DatabaseException.UndefinedColumn(SQLErrorDetail(entityType = EntityType.COLUMN, entityName = name))
            }
        }
}
