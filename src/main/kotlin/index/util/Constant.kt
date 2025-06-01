package index.util

import java.text.Collator

data class KeySchema(
    val columns: List<Column>
)

data class Column(
    val name: String,
    val type: ColumnType,
    val descending: Boolean,
    val collation: Collator? = null
)

enum class ColumnType {
    INT, LONG, STRING, BOOLEAN, BYTE, SHORT, FLOAT, DOUBLE, LOCAL_DATE, LOCAL_DATE_TIME, INSTANT, UUID, BYTES
}
