package index.btree.node

import config.PageConfig
import index.serializer.BaseKeySerializer
import index.serializer.PageIDSerializer
import index.util.NodeSplitData
import storageEngine.util.PageType
import java.nio.ByteBuffer


class LeafNode<K>(
    pageConfig: PageConfig,
    pageId: Long = -1,
    data: ByteBuffer,
    pageType: PageType = PageType.LEAF_NODE,
    keySerializer: BaseKeySerializer<K>,
    private val valueSerializer: PageIDSerializer = PageIDSerializer()
): Node<K>(pageConfig, pageId, data, pageType, keySerializer){

    val next: Long
        get() = rightSiblingPageId

    val prev: Long
        get() = leftSiblingPageId

    val keyCount: Int
        get() = recordCount

    val valueCount: Int
        get() = recordCount

    fun insert(slotId: Int, key: ByteArray, data: ByteArray, keySerializer: BaseKeySerializer<K>){
        insertData(slotId, key, data)
    }

    fun delete(slotId: Int){
        deleteData(slotId)
    }

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
    fun split(): NodeSplitData {
        val promotionKeyIdx = promotionKeyIdx()
        val (splitKeys, splitValues) = splitData(promotionKeyIdx)
        val totalRecordCount = recordCount
        val (promotionKey, leftMostChildPageId) = deleteData(totalRecordCount - 1)
        return NodeSplitData(
            splitKeys, splitValues, promotionKey, leftMostChildPageId
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
        val totalRecordCount = recordCount
        for(slotId in promotionKeyIdx until totalRecordCount){
            val (key, value) = deleteData(slotId)
            keyList.add(key)
            values.add(value)
        }
        return keyList to values
    }

    /**
     * Update the linked list of the leaf node.
     * */
    fun linkNewSiblingNode(siblingNode: LeafNode<K>){
        val nextTemp = rightSiblingPageId
        siblingNode.rightSiblingPageId = nextTemp
        siblingNode.leftSiblingPageId = this.pageId
        rightSiblingPageId = siblingNode.pageId
    }

    override fun deleteAllData(): Pair<List<ByteArray>, List<ByteArray>>{
        val endSlotId = recordCount - 1
        val resultKey = MutableList(recordCount){ByteArray(0x00)}
        val resultValue = MutableList(recordCount){ByteArray(0x00)}
        for(slotId in 0 .. endSlotId){
            val (key, value) = deleteData(slotId)
            resultKey[slotId] = key
            resultValue[slotId] = value
        }
        return resultKey to resultValue
    }

    override fun appendAllData(keys: List<ByteArray>, values: List<ByteArray>) {
        for(slotId in keys.indices){
            insertData(recordCount + slotId, keys[slotId], values[slotId])
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
        if(isLeft(targetNode.pageId, parentNode, keyIdx)){
            val (key, value) = targetNode.deleteData(0)
            val insertSlotId = search(key).first
            insertData(insertSlotId, key, value)
            parentNode.updateKey(keyIdx, key)
        } else{
            val recordCount = targetNode.recordCount
            val (key, value) = targetNode.deleteData(recordCount - 1)
            val insertSlotId = search(key).first
            insertData(insertSlotId, key, value)
            parentNode.updateKey(keyIdx-1, key)
        }
    }

    /**
     * Merge right node into left one.
     *
     * @see Node.merge
     * */
    override fun merge(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int) {
        orderNode(targetNode, parentNode, keyIdx).let {
            (separationKey, lNode, rNode) ->

            val leftNode = lNode as LeafNode<K>
            val rightNode = rNode as LeafNode<K>
            val (rKey, rValue) = rightNode.deleteAllData()
            logger.info { "Merge Internal: leftNode: ${leftNode.hashCode()} rightNode: ${rightNode.hashCode()}" }
            lNode.appendAllData(rKey, rValue)

            val newRightSiblingPageId = rNode.rightSiblingPageId
            lNode.rightSiblingPageId = newRightSiblingPageId
            parentNode.deleteData(separationKey)
        }
    }
}