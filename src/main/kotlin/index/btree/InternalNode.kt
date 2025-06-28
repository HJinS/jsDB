package index.btree


/**
 * Pi -> Ki <= key < Ki+1
 * */
class InternalNode(
    keys: MutableList<ByteArray>,
    maxKeys: Int,
    private val children: MutableList<Node>
): Node(false, keys, maxKeys) {
    fun moveToChild(index: Int): Node = children[index]

    fun updateNode(idx: Int, promotionKey: ByteArray, childNode: Node) {
        children.add(idx, childNode)
        keys.add(idx, promotionKey)
    }

    /**
     * internal node
     *  - mid(promote key) 값을 floor(len / 2)로 지정
     *  - mid 값은 promote 됨
     *  - 0 ~ mid-1, mid+1 ~ len-1 key 분리(닫힌 구간)
     *  - 0 ~ mid, mid+1 ~ len-1 자식 분리(닫힌 구간)
     * */
    fun split(): Pair<MutableList<ByteArray>, MutableList<Node>> {
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys = splitKey()
        val splitChildren = splitChildPointer(promotionKeyIdx)
        keys.removeLast()
        return splitKeys to splitChildren
    }

    private fun splitChildPointer(promotionKeyIdx: Int): MutableList<Node>{
        val childrenSize = children.size
        val splitChildPointer = children.takeLast(childrenSize - promotionKeyIdx - 1).toMutableList()
        children.subList(promotionKeyIdx+1, childrenSize).clear()
        return splitChildPointer
    }
}