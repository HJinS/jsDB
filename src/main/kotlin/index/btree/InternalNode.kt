package index.btree

import index.util.KeySchema


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

    private fun removeFirstChild(): Node = children.removeFirst()

    private fun removeLastChild(): Node = children.removeLast()

    internal fun removeChildrenAt(idx: Int) = children.removeAt(idx)

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

    /**
     * ### redistribution
     * #### borrow from the left sibling
     * separateKey - (keyIdx - 1)
     * 1. Take a separateKey from the parent Node and insert to my node as the smallest key
     * 2. Insert the biggest key of the left sibling node to the location of the parent's separateKey
     * 3. Insert a right most child pointer of the left sibling to me as a left most key
     *
     * #### borrow from the right sibling
     * separateKey - (keyIdx)
     * 1. Take a separateKey from the parent Node and insert to my node as the biggest key
     * 2. Insert the smallest key of the right sibling node to the location of the parent's separateKey
     * 3. Insert a left most child pointer of the right sibling to me as the right most key
     * */
    override fun redistribute(targetNode: Node, parentNode: InternalNode, keyIdx: Int, schema: KeySchema){
        val node: InternalNode = targetNode as InternalNode

        // borrow from right sibling
        if(isLeft(targetNode, parentNode, keyIdx)){
            // 부모의 구분키 제거
            val removedParentKey = parentNode.keys.removeAt(keyIdx)
            // 자신의 키에 추가
            keys.addLast(removedParentKey)

            // 오른쪽 형제의 가장 작은 키 제거
            val siblingKey = node.removeFirstKey()
            // 부모의 삭제된 자리에 키 추가
            parentNode.keys.add(keyIdx, siblingKey)

            // 오른쪽 형제의 가장 왼쪽 child point 제거
            val siblingChild = node.removeFirstChild()
            // 자신의 가장 오른쪽 childPoint 로 추가
            children.addLast(siblingChild)
        } else{
            // 부모 구분키 제거(구분키는 keyIdx-1 이어야함.)
            val removedParentKey = parentNode.keys.removeAt(keyIdx-1)
            // 지신의 키에 추가
            keys.addFirst(removedParentKey)

            // 왼쪽 형제의 가장 큰 키 제거
            val siblingKey = node.keys.removeLast()
            // 부모의 삭제된 자리에 키 추가
            parentNode.keys.add(keyIdx-1, siblingKey)

            // 왼쪽 형제의 가장 오른쪽 child point 제거
            val siblingChild = node.removeLastChild()
            // 자신의 가장 왼쪽 childPoint 로 추가
            children.addFirst(siblingChild)
        }
    }

    override fun merge(targetNode: Node, parentNode: InternalNode, keyIdx: Int, schema: KeySchema) {
        val leftNode: InternalNode
        val rightNode: InternalNode
        orderNode(targetNode, parentNode, keyIdx).let {
            (separationKey, lNode, rNode) ->
            leftNode = lNode as InternalNode
            rightNode = rNode as InternalNode
            val extractedKey = rightNode.keys
            logger.info { "Merge Internal: leftNode: ${leftNode.hashCode()} rightNode: ${rightNode.hashCode()}" }
            leftNode.keys.addLast(parentNode.keys[separationKey])
            leftNode.keys.addAll(extractedKey)
            leftNode.children.addAll(rightNode.children)
            parentNode.keys.removeAt(separationKey)
            parentNode.children.removeAt(separationKey+1)
        }
    }
}