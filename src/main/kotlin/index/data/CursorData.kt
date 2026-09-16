package index.data

data class CursorData(
    val key: ByteArray,
    val value: ByteArray, 
    val recordCount: Int,
    val nextPageId: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CursorData

        if (recordCount != other.recordCount) return false
        if (nextPageId != other.nextPageId) return false
        if (!key.contentEquals(other.key)) return false
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = recordCount
        result = 31 * result + nextPageId.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "CursorData(key=${key.contentToString()}, value=${value.contentToString()}, " +
            "recordCound=$recordCount, nextPageId=$nextPageId)"
    }
}

