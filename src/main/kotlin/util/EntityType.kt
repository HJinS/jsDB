package util

/** What kind of catalog object an [SQLErrorDetail] is about — [displayName] renders into the error message ([ErrorDetail.toMessage]). */
enum class EntityType(val displayName: String) {
    TABLE("table"),
    COLUMN("column"),
    PRIMARY_KEY("primary key"),
    PRIMARY_INDEX("primary index"),
    INDEX("index"),
    INDEX_KEY_COLUMN("index key column"),
    CATALOG_ROW("catalog row"),
    ROW("row")
}
