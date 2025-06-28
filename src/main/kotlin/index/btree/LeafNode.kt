package index.btree

class LeafNode(
    keys: MutableList<ByteArray>,
    maxKeys: Int,
    private val values: MutableList<Any?>,
    private var next: LeafNode? = null,
    private var prev: LeafNode? = null
): Node(true, keys, maxKeys){
    fun insert(key: ByteArray, originalData: List<Any?> , comparator: Comparator<ByteArray>){
        val idx = search(key, comparator)
        keys.add(idx, key)
        values.add(idx, originalData)
    }

    /**
     * leaf node
     * - 추가로 value split 필요
     * - key < maxKeys 인 경우 split
     * - mid(promote key) 값을 floor(len / 2)로 지정
     * - 0 ~ mid, mid+1 ~ len-1 좌우 분리(닫힌 구간)
     * - promote key 도 leaf node 에 남아야함
     * */
    fun split(): Pair<MutableList<ByteArray>, MutableList<Any?>> {
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys = splitKey()
        val splitValues = splitValues(promotionKeyIdx)
        return splitKeys to splitValues
    }

    private fun splitValues(promotionKeyIdx: Int): MutableList<Any?>{
        val valueSize = values.size
        val splitValues =  values.takeLast(valueSize - promotionKeyIdx - 1).toMutableList()
        values.subList(promotionKeyIdx+1, valueSize).clear()
        return splitValues
    }

    // split 으로 생긴 오른쪽의 새로 생긴 노드를 연결
    fun linkNewSiblingNode(siblingNode: LeafNode){
        val nextTemp = next
        siblingNode.next = nextTemp
        siblingNode.prev = this
        next = siblingNode
    }
}