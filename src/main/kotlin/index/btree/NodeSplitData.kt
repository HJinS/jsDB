package index.btree

data class NodeSplitData(
    val splitKeys: MutableList<ByteArray>,
    val splitValues: MutableList<ByteArray>,
    val promotionKey: ByteArray,
    val leftMostChildPageId: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeSplitData

        if (leftMostChildPageId != other.leftMostChildPageId) return false
        if (splitKeys.size != other.splitKeys.size ||
            splitKeys.zip(other.splitKeys).any { (a, b) -> !a.contentEquals(b) }) return false
        if (splitValues.size != other.splitValues.size ||
            splitValues.zip(other.splitValues).any { (a, b) -> !a.contentEquals(b) }) return false
        if (!promotionKey.contentEquals(other.promotionKey)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = leftMostChildPageId.hashCode()
        result = 31 * result + splitKeys.fold(1) { acc, arr -> 31 * acc + arr.contentHashCode() }
        result = 31 * result + splitValues.fold(1) { acc, arr -> 31 * acc + arr.contentHashCode() }
        result = 31 * result + promotionKey.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "NodeSplitData(splitKeys=${splitKeys.map { it.contentToString() }}, " +
            "splitValues=${splitValues.map { it.contentToString() }}, " +
            "promotionKey=${promotionKey.contentToString()}, " +
            "leftMostChildPageId=$leftMostChildPageId)"
    }
}
