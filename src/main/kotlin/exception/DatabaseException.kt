package exception

sealed class DatabaseException(message: String?, cause: Throwable? = null): RuntimeException(message, cause) {
    class IndexAlreadyExistsException(
        indexName: String, cause: Throwable? = null
    ): DatabaseException("Index '$indexName' already exists with a different schema.", cause)

    class TableAlreadyExistsException(
        tableName: String, cause: Throwable? = null
    ): DatabaseException("Table '$tableName' already exists with a different schema.", cause)

    class ColumnAlreadyExistsException(
        tableId: Long, columnName: String, cause: Throwable? = null
    ): DatabaseException("Column '$columnName' already exists on tableID: $tableId with a different schema.", cause)

    class PrimaryIndexNotYetCreatedException(
        tableName: String, cause: Throwable? = null
    ): DatabaseException("Table [$tableName] doesn't have primary index yet.", cause)

    class PrimaryKeyMustNotBeNullableException(
        tableName: String, columnNames: List<String>, cause: Throwable? = null
    ): DatabaseException(
        "Primary key column(s) ${columnNames.joinToString(", ") { "'$it'" }} on table '$tableName' must not be nullable.",
        cause
    )

    class PrimaryIndexAlreadyExistsException(
        tableName: String, cause: Throwable? = null
    ): DatabaseException(
        "Primary index already exists on table '$tableName'.",
        cause
    )

    class UnknownKeyColumnException(
        indexName: String, tableName: String, indexColumnName: List<String>, cause: Throwable? = null
    ): DatabaseException(
        "Index key column(s) [${indexColumnName.joinToString(", ") { "'$it'" }}] on index '$indexName' doesn't exist on table '$tableName'.",
        cause
    )

    class DuplicateColumnNameException(
        tableName: String, columnNames: List<String>, cause: Throwable? = null
    ): DatabaseException(
        "Duplicate column name(s) ${columnNames.joinToString(", ") { "'$it'" }} in table '$tableName' definition.",
        cause
    )
}
