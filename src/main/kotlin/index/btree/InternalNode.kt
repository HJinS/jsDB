package index.btree


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
     * Split the internal node into 2 pieces.
     * - PromotionKey: floor(len / 2)
     * - PromotionKey goes to parent node.
     * - Key separation: [0, promotionKey-1], [promotionKey+1, len-1]
     * - Children separation: [0, promotionKey], [promotionKey+1, len-1]
     *
     * @see splitKey
     * @see splitChildPointer
     * @see promotionKeyIdx
     * @return key, value for a new node.
     * */
    fun split(): Pair<MutableList<ByteArray>, MutableList<Node>> {
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys = splitKey(promotionKeyIdx)
        val splitChildren = splitChildPointer(promotionKeyIdx)
        keys.removeLast()
        return splitKeys to splitChildren
    }

    /**
     * Split keys into 2 pieces.
     *
     * [0, [promotionKeyIdx]-1], [[promotionKeyIdx]+1, len-1]
     *
     * @param promotionKeyIdx Standard for splitting values
     * @return split key array
     * */
    private fun splitKey(promotionKeyIdx: Int): MutableList<ByteArray>{
        val keySize = keys.size
        val splitKeys =  keys.takeLast(keySize - promotionKeyIdx - 1).toMutableList()
        keys.subList(promotionKeyIdx+1, keySize).clear()
        return splitKeys
    }

    /**
     * Split children into 2 pieces.
     *
     * [0, [promotionKeyIdx]], [[promotionKeyIdx]+1, len-1]
     * @return split children.
     * */
    private fun splitChildPointer(promotionKeyIdx: Int): MutableList<Node>{
        val childrenSize = children.size
        val splitChildPointer = children.takeLast(childrenSize - promotionKeyIdx - 1).toMutableList()
        children.subList(promotionKeyIdx+1, childrenSize).clear()
        return splitChildPointer
    }

    /**
     * ### redistribution
     * Rotate keys using parent node.
     *
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
     *
     * @param targetNode The target node to get key from.
     * @param parentNode Parent node to update new separation key.
     * @param keyIdx Separation key.
     * */
    override fun redistribute(targetNode: Node, parentNode: InternalNode, keyIdx: Int){
        val node: InternalNode = targetNode as InternalNode

        // Borrow from right sibling
        if(isLeft(targetNode, parentNode, keyIdx)){
            // Delete separation key from parent node.
            val removedParentKey = parentNode.keys.removeAt(keyIdx)
            // Add the separation key as the biggest key to me.
            keys.addLast(removedParentKey)

            // Delete the smallest key from right sibling.
            val siblingKey = node.removeFirstKey()
            // Insert the smallest key to parent node as new separation key.
            parentNode.keys.add(keyIdx, siblingKey)

            // Delete the smallest child pointer from the right sibling.
            val siblingChild = node.removeFirstChild()
            // Insert the pointer to me as the biggest one.
            children.addLast(siblingChild)
        } else{
            // Delete separation key from parent node.
            // In this case, the separation key should be keyIdx - 1
            val removedParentKey = parentNode.keys.removeAt(keyIdx-1)
            // Add the separation key as the smallest key to me.
            keys.addFirst(removedParentKey)

            // Delete the biggest key from left sibling.
            val siblingKey = node.keys.removeLast()
            // Insert the biggest key to parent node as new separation key.
            parentNode.keys.add(keyIdx-1, siblingKey)

            // Delete the biggest child pointer from the left sibling.
            val siblingChild = node.removeLastChild()
            // Insert the pointer to me as the smallest one.
            children.addFirst(siblingChild)
        }
    }

    /**
     * Merge right node into left one.
     *
     * @see Node.merge
     * */
    override fun merge(targetNode: Node, parentNode: InternalNode, keyIdx: Int) {
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