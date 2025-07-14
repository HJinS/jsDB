package index.btree


/**
 * Pi -> Ki <= key < Ki+1
 * */
class InternalNode(
    keys: MutableList<ByteArray>,
    maxKeys: Int,
    private val children: MutableList<Node>
): Node(false, keys, maxKeys) {

    val childCount: Int
        get() = children.size

    fun moveToChild(index: Int): Node = children[index]

    fun insert(idx: Int, key: ByteArray, childNode: Node) {
        children.add(idx+1, childNode)
        keys.add(idx, key)
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

    private fun splitKey(): MutableList<ByteArray>{
        val keySize = keys.size
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys =  keys.takeLast(keySize - promotionKeyIdx - 1).toMutableList()
        keys.subList(promotionKeyIdx+1, keySize).clear()
        return splitKeys
    }

    private fun splitChildPointer(promotionKeyIdx: Int): MutableList<Node>{
        val childrenSize = children.size
        val splitChildPointer = children.takeLast(childrenSize - promotionKeyIdx - 1).toMutableList()
        children.subList(promotionKeyIdx+1, childrenSize).clear()
        return splitChildPointer
    }
}