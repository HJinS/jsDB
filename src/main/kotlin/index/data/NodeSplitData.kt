package index.data

/**
 * What a [index.btree.node.LeafNode] and [index.btree.node.InternalNode])
 * hands back to [index.btree.BTree.split] to build the new right sibling and promote a key to the parent.
 *
 * @property splitKeys The right piece's keys, in order.
 * @property splitValues The right piece's values (leaf) / child pointers (internal), aligned with
 *   [splitKeys].
 * @property promotionKey The key handed up to the parent as the new separator.
 * @property leftMostChildPageId Only meaningful for an [index.btree.node.InternalNode] split — its
 *   new right sibling's leftmost child pointer. A [index.btree.node.LeafNode] split always passes
 *   a literal `-1` here (not [util.INVALID_PAGE_ID]) since it's never read for a leaf.
 *
 * `equals`/`hashCode`/`toString` are overridden because the default `data class` versions would
 * compare [splitKeys]/[splitValues] by `ByteArray` reference identity, not content — needed for
 * test assertions on split results.
 * */
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
