package util

class SQLErrorDetail(
    reason: String? = null,
    val entityType: EntityType,
    val entityName: String? = null,
    val tableName: String? = null,
    val columnNames: List<String> = emptyList()
) : ErrorDetail(reason) {
    override fun StringBuilder.appendExtra() {
        append(" ").append(entityType.displayName.replaceFirstChar { it.uppercase() })
        entityName?.let { append(" '$it'") }
        tableName?.let { append(" on table '$it'") }
        if (columnNames.isNotEmpty()) append(" (columns: ${columnNames.joinToString(", ") { "'$it'" }})")
    }
}
