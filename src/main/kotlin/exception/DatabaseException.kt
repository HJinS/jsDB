package exception

import util.SQLErrorDetail
import util.SqlState

/** Failures from [database.DataBase] — table/index/column DDL validation (duplicates, undefined references, PK nullability). */
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

    /** Dropping this object directly isn't allowed because something else still depends on it —
     * e.g. a table's primary index can only be removed via DROP TABLE (issue #49). */
    class DependentObjectsExist(detail: SQLErrorDetail, cause: Throwable? = null):
        DatabaseException(SqlState.DEPENDENT_OBJECTS_STILL_EXIST, detail, cause)
}
