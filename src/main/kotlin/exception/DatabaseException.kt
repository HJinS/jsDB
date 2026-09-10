package exception

import util.SQLErrorDetail
import util.SqlState

sealed class DatabaseException(
    sqlState: SqlState,
    detail: SQLErrorDetail,
    cause: Throwable? = null
): RuntimeException(detail.toMessage(sqlState), cause) {

    class DuplicateTable(detail: SQLErrorDetail, cause: Throwable? = null):
        DatabaseException(SqlState.DUPLICATE_TABLE, detail, cause)

    class DuplicateColumn(detail: SQLErrorDetail, cause: Throwable? = null):
        DatabaseException(SqlState.DUPLICATE_COLUMN, detail, cause)

    class DuplicateObject(detail: SQLErrorDetail, cause: Throwable? = null):
        DatabaseException(SqlState.DUPLICATE_OBJECT, detail, cause)

    class UndefinedColumn(detail: SQLErrorDetail, cause: Throwable? = null):
        DatabaseException(SqlState.UNDEFINED_COLUMN, detail, cause)

    class UndefinedObject(detail: SQLErrorDetail, cause: Throwable? = null):
        DatabaseException(SqlState.UNDEFINED_OBJECT, detail, cause)

    class NotNullViolation(detail: SQLErrorDetail, cause: Throwable? = null):
        DatabaseException(SqlState.NOT_NULL_VIOLATION, detail, cause)
}
