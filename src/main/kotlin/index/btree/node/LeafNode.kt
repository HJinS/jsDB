package index.btree.node

import config.IndexConfig
import index.serializer.KeySerializer
import index.util.NodeSplitData
import mu.KotlinLogging
import storageEngine.page.SlottedPage


class LeafNode<K>(
    indexConfig: IndexConfig,
    page: SlottedPage,
    keySerializer: KeySerializer<K>,
): Node<K>(indexConfig, page, keySerializer) {

    val logger = KotlinLogging.logger {}

    val next: Long
        get() = page.rightSiblingPageId

    val prev: Long
        get() = page.leftSiblingPageId

    val valueCount: Int
        get() = page.recordCount

    /**
     * Split the leaf node into 2 pieces.
     *
     * PromotionKey also remains at leaf node.
     * - PromotionKey: floor(len / 2)
     * - PromotionKey goes to parent node.
     * - Key separation: [0, promotionKey-1], [promotionKey, len-1]
     * - Value separation: [0, promotionKey-1], [promotionKey, len-1]
     *
     * @see splitData
     * @see promotionKeyIdx
     * @return key, value for a new node.
     * */
    fun split(): NodeSplitData {
        val promotionKeyIdx = promotionKeyIdx()
        val (splitKeys, splitValues) = splitData(promotionKeyIdx)
        val totalRecordCount = page.recordCount
        val (promotionKey, _) = page.deleteData(totalRecordCount - 1)
        return NodeSplitData(
            splitKeys, splitValues, promotionKey, -1
        )
    }

    /**
     * Split keys into 2 pieces.
     * [[promotionKeyIdx], len-1]
     *
     * @param promotionKeyIdx Standard for splitting values
     * @return split key array
     * */
    private fun splitData(promotionKeyIdx: Int): Pair<MutableList<ByteArray>, MutableList<ByteArray>>{
        val keyList = mutableListOf<ByteArray>()
        val values = mutableListOf<ByteArray>()
        val totalRecordCount = page.recordCount
        for(slotId in promotionKeyIdx until totalRecordCount){
            val (key, value) = page.deleteData(slotId)
            keyList.add(key)
            values.add(value)
        }
        return keyList to values
    }

    /**
     * Update the linked list of the leaf node.
     * */
    fun linkNewSiblingNode(siblingNode: LeafNode<K>){
        val nextTemp = page.rightSiblingPageId
        siblingNode.page.rightSiblingPageId = nextTemp
        siblingNode.page.leftSiblingPageId = this.page.pageId
        page.rightSiblingPageId = siblingNode.page.pageId
    }

    override fun deleteAllData(): Pair<List<ByteArray>, List<ByteArray>>{
        val endSlotId = page.recordCount - 1
        val resultKey = MutableList(page.recordCount){ByteArray(0x00)}
        val resultValue = MutableList(page.recordCount){ByteArray(0x00)}
        for(slotId in 0 .. endSlotId){
            val (key, value) = page.deleteData(slotId)
            resultKey[slotId] = key
            resultValue[slotId] = value
        }
        return resultKey to resultValue
    }

    override fun appendAllData(keys: List<ByteArray>, values: List<ByteArray>) {
        for(slotId in keys.indices){
            page.insertData(page.recordCount + slotId, keys[slotId], values[slotId])
        }
    }

    /**
     * ### Leaf Redistribution
     * Do not rotate keys.
     *
     * Get the key from the sibling directly.
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
    override fun redistribute(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int){
        // borrow from right sibling
        if(isLeft(targetNode.page.pageId, parentNode, keyIdx)){
            val (key, value) = targetNode.deleteData(0)
            insert(key, value)
            parentNode.updateKey(keyIdx, key)
        } else{
            val recordCount = targetNode.keyCount
            val (key, value) = targetNode.deleteData(recordCount - 1)
            insert(key, value)
            parentNode.updateKey(keyIdx-1, key)
        }
    }

    /**
     * Merge right node into left one.
     *
     * @see Node.merge
     * */
    override fun merge(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int): Pair<Long, Long> {
        orderNode(targetNode, parentNode, keyIdx).let {
            (separationKey, lNode, rNode) ->

            val leftNode = lNode as LeafNode<K>
            val rightNode = rNode as LeafNode<K>
            val (rKey, rValue) = rightNode.deleteAllData()
            logger.info { "Merge Internal: leftNode: ${leftNode.hashCode()} rightNode: ${rightNode.hashCode()}" }
            leftNode.appendAllData(rKey, rValue)

            val newRightSiblingPageId = rightNode.page.rightSiblingPageId
            leftNode.page.rightSiblingPageId = newRightSiblingPageId
            parentNode.deleteData(separationKey)
            return leftNode.pageId to rightNode.pageId
        }
    }
}