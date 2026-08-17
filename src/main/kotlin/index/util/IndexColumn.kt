package index.util

import java.text.Collator

data class IndexColumn(
    val name: String,
    val type: ColumnType,
    val descending: Boolean,
    val collation: Collator? = null
)
