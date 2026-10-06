package schema

import exception.DatabaseException
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail
import util.requireOrThrow

/** A table's (or a primary index's row-shaped value's) column layout, in storage order. */
data class RowSchema(
    val rowColumns: List<RowColumn>
) {
    /** @throws DatabaseException ([ErrorCode.UNDEFINED_COLUMN]) if [name] isn't one of [rowColumns]. */
    fun columnIndex(name: String): Int =
        rowColumns.indexOfFirst {
            it.name == name
        }.also {
            requireOrThrow(it >= 0) {
                DatabaseException(
                    ErrorCode.UNDEFINED_COLUMN,
                    SQLErrorDetail(entityType = EntityType.COLUMN, entityName = name)
                )
            }
        }
}
