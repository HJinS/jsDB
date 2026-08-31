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
}
