package exception

import util.SQLErrorDetail
import util.SqlState

/** Failures from [catalog.CatalogManager] — resolving, registering, or updating a table/index/column catalog row. */
sealed class CatalogException(
    sqlState: SqlState,
    detail: SQLErrorDetail,
    cause: Throwable? = null
): RuntimeException(detail.toMessage(sqlState), cause) {

    /** A catalog row doesn't match its schema — under normal operation this can't happen; it means the stored data itself is corrupted. */
    class CorruptedRow(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.INTERNAL_ERROR, detail, cause)

    /** The table/index definition is itself structurally invalid (e.g. no key columns at all, no columns at all). */
    class InvalidDefinition(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.INVALID_TABLE_DEFINITION, detail, cause)

    class UndefinedTable(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.UNDEFINED_TABLE, detail, cause)

    class UndefinedObject(detail: SQLErrorDetail, cause: Throwable? = null):
        CatalogException(SqlState.UNDEFINED_OBJECT, detail, cause)
}
