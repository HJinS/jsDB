package index.util

data class BTreeSchema(
    val keySchema: IndexKeySchema,
    val rowSchema: RowSchema
)
