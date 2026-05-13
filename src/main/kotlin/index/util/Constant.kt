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

// degree = maxKeys
const val MAX_KEYS = 64


data class NodeSplitData(
    val splitKeys: MutableList<ByteArray>,
    val splitValues: MutableList<ByteArray>,
    val promotionKey: ByteArray,
    val leftMostChildPageId: Long
)

enum class BTreeOptMode {SELECT, INSERT, DELETE, UPDATE}

