package index.btree

import java.util.Collections

class LeafNode(
    keys: MutableList<ByteArray>,
    maxKeys: Int,
    values: MutableList<ByteArray>,
    next: LeafNode? = null,
    prev: LeafNode? = null
): Node(true, keys, maxKeys){

    private var _next = next
    private var _prev = prev
    private val _values = values

    val next: LeafNode?
        get() = _next

    val prev: LeafNode?
        get() = _prev

    val values: List<ByteArray>
        get() = Collections.unmodifiableList(_values)

    fun insert(idx: Int, key: ByteArray, data: ByteArray){
        keys.add(idx, key)
        _values.add(idx, data)
    }

    fun delete(keyIdx: Int){
        keys.removeAt(keyIdx)
        _values.removeAt(keyIdx)
    }

    private fun removeFirstValue(): ByteArray = _values.removeFirst()

    private fun removeLastValue(): ByteArray = _values.removeLast()

    /**
     * Split the leaf node into 2 pieces.
     *
     * PromotionKey also remains at leaf node.
     * - PromotionKey: floor(len / 2)
     * - PromotionKey goes to parent node.
     * - Key separation: [0, promotionKey-1], [promotionKey, len-1]
     * - Value separation: [0, promotionKey-1], [promotionKey, len-1]
     *
     * @see splitKey
     * @see splitValues
     * @see promotionKeyIdx
     * @return key, value for a new node.
     * */
    fun split(): Pair<MutableList<ByteArray>, MutableList<ByteArray>> {
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys = splitKey(promotionKeyIdx)
        val splitValues = splitValues(promotionKeyIdx)
        return splitKeys to splitValues
    }


    /**
     * Split keys into 2 pieces.
     *
     * [0, [promotionKeyIdx]-1], [[promotionKeyIdx], len-1]
     *
     * @param promotionKeyIdx Standard for splitting values
     * @return split keys.
     * */
    private fun splitKey(promotionKeyIdx: Int): MutableList<ByteArray>{
        val keySize = keys.size
        val splitKeys =  keys.takeLast(keySize - promotionKeyIdx).toMutableList()
        keys.subList(promotionKeyIdx, keySize).clear()
        return splitKeys
    }

    /**
     * Split values into 2 pieces.
     *
     * [0, [promotionKeyIdx]-1], [[promotionKeyIdx], len-1]
     *
     * @param promotionKeyIdx Standard for splitting values
     * @return split values.
     * */
    private fun splitValues(promotionKeyIdx: Int): MutableList<ByteArray>{
        val valueSize = _values.size
        val splitValues =  _values.takeLast(valueSize - promotionKeyIdx).toMutableList()
        _values.subList(promotionKeyIdx, valueSize).clear()
        return splitValues
    }

    /**
     * Update the linked list of the leaf node.
     * */
    fun linkNewSiblingNode(siblingNode: LeafNode){
        val nextTemp = _next
        siblingNode._next = nextTemp
        siblingNode._prev = this
        _next = siblingNode
    }

    /**
     * ### Leaf redistribution
     * Do not rotate keys.
     *
     * Get the key from sibling directly.
     *
     * #### borrow from the left sibling
     * separateKey - (keyIdx - 1)
     * 1. Take the biggest key of the left sibling.
     * 2. Take a right most value of the left sibling.
     * 3. Insert key and value to me as the smallest one
     * 4. Update the parent node's separator keys to the key of 3.
     *
     * #### borrow from the right sibling
     * separateKey - (keyIdx)
     * 1. Take the smallest key of the right sibling.
     * 2. Take the left most value of the right sibling.
     * 3. Insert key and value to me as the biggest one.
     * 4. Update the parent node's separator keys to the new smallest key of the right sibling
     *
     * @param targetNode The target node to get key from.
     * @param parentNode Parent node to update new separation key.
     * @param keyIdx Separation key.
     * */
    override fun redistribute(targetNode: Node, parentNode: InternalNode, keyIdx: Int){
        val node: LeafNode = targetNode as LeafNode
        logger.info { "Redistribute: ${node.keys} ${node._values}" }
        // borrow from right sibling
        if(isLeft(node, parentNode, keyIdx)){
            logger.info { "Redistribute: leftNode ${this._values} rightNode ${node._values}" }
            val key = node.removeFirstKey()
            val value = node.removeFirstValue()
            keys.addLast(key)
            _values.addLast(value)
            parentNode.keys[keyIdx] = node.keys[0]
        } else{
            logger.info { "Redistribute: leftNode ${node._values} rightNode ${this._values}" }
            val key = node.removeLastKey()
            val value = node.removeLastValue()
            keys.addFirst(key)
            _values.addFirst(value)
            parentNode.keys[keyIdx-1] = key
        }
    }

    /**
     * Merge right node into left one.
     *
     * @see Node.merge
     * */
    override fun merge(targetNode: Node, parentNode: InternalNode, keyIdx: Int) {
        val node: LeafNode = targetNode as LeafNode
        val leftNode: LeafNode
        val rightNode: LeafNode
        logger.info { "Merge: ${node.keys} ${node._values}" }
        orderNode(node, parentNode, keyIdx).let {
            (separationKey, lNode, rNode) ->
            leftNode = lNode as LeafNode
            rightNode = rNode as LeafNode
            logger.info { "Merge: leftNode ${leftNode._values} rightNode ${rightNode._values}" }
            val extractedKey = rightNode.keys
            val newNextNode = rightNode.next
            leftNode.keys.addAll(extractedKey)
            leftNode._values.addAll(rightNode.values)
            parentNode.keys.removeAt(separationKey)
            parentNode.removeChildrenAt(separationKey+1)

            leftNode._next = newNextNode
            newNextNode?._prev = leftNode
        }
    }
}