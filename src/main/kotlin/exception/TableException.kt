package exception

import util.SQLErrorDetail
import util.SqlState

sealed class TableException(
    sqlState: SqlState,
    detail: SQLErrorDetail,
    cause: Throwable? = null
): RuntimeException(detail.toMessage(sqlState), cause) {

    class RowNotFound(detail: SQLErrorDetail, cause: Throwable? = null):
        TableException(SqlState.NO_DATA, detail, cause)

    class UniqueViolation(detail: SQLErrorDetail, cause: Throwable? = null):
        TableException(SqlState.UNIQUE_VIOLATION, detail, cause)

    class UndefinedIndex(detail: SQLErrorDetail, cause: Throwable? = null):
        TableException(SqlState.UNDEFINED_OBJECT, detail, cause)

    class TooManyOrderColumns(detail: SQLErrorDetail, cause: Throwable? = null):
        TableException(SqlState.INVALID_COLUMN_REFERENCE, detail, cause)

    class OrderColumnMismatch(detail: SQLErrorDetail, cause: Throwable? = null):
        TableException(SqlState.INVALID_COLUMN_REFERENCE, detail, cause)

    class UnsupportedSortDirection(detail: SQLErrorDetail, cause: Throwable? = null):
        TableException(SqlState.INVALID_COLUMN_REFERENCE, detail, cause)

    class CorruptedIndex(detail: SQLErrorDetail, cause: Throwable? = null):
        TableException(SqlState.INTERNAL_ERROR, detail, cause)
}
