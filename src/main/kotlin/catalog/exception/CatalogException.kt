package catalog.exception

sealed class CatalogException(message: String?, cause: Throwable? = null): RuntimeException(message, cause) {
    class CorruptedCatalogException(
        catalogName: String,
        cause: Throwable? = null
    ): CatalogException(
        "Catalog row does not match expected schema for $catalogName. Data may be corrupted or schema changed.",
        cause
    )

    class IndexKeyViolation(
        cause: Throwable? = null
    ): CatalogException(
        "Index must have at least one key column.", cause
    )
}
