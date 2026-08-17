package index.util

data class NodeSplitData(
    val splitKeys: MutableList<ByteArray>,
    val splitValues: MutableList<ByteArray>,
    val promotionKey: ByteArray,
    val leftMostChildPageId: Long
)
