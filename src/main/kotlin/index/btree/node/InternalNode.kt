package index.btree.node

import config.PageConfig
import index.serializer.BaseKeySerializer
import index.serializer.PageIDSerializer
import index.util.NodeSplitData
import storageEngine.util.PageType
import java.nio.ByteBuffer


class InternalNode<K>(
    pageConfig: PageConfig,
    pageId: Long = -1,
    data: ByteBuffer,
    pageType: PageType = PageType.INTERNAL_NODE,
    keySerializer: BaseKeySerializer<K>,
    private val valueSerializer: PageIDSerializer = PageIDSerializer()
): Node<K>(pageConfig, pageId, data, pageType, keySerializer) {

    fun childPageId(index: Int): Long = if(index == 0) leftMostChildPageId else valueSerializer.deserialize(getData(index).second)

    fun updateValue(slotId: Int, newChildPageId: Long) {
        val (key, _) = getData(slotId)
        updateData(slotId, key, valueSerializer.serialize(newChildPageId))
    }

    fun updateKey(slotId: Int, key: ByteArray){
        val (_, value) = getData(slotId)
        updateData(slotId, key, value)
    }

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
    fun split(): NodeSplitData {
        val promotionKeyIdx = promotionKeyIdx()
        val (splitKeyList, splitChildrenId) = splitData(promotionKeyIdx)
        val totalRecordCount = recordCount
        val (promotionKey, leftMostChildPageId) = deleteData(totalRecordCount - 1)
        return NodeSplitData(
            splitKeyList, splitChildrenId, promotionKey, leftMostChildPageId
        )
    }

    override fun deleteAllData(): Pair<MutableList<ByteArray>, MutableList<ByteArray>>{
        val endSlotId = recordCount - 1
        val resultKey = MutableList(recordCount){ByteArray(0x00)}
        val resultValue = MutableList(recordCount+1){ByteArray(0x00)}
        resultValue[0] = valueSerializer.serialize(leftMostChildPageId)
        for(slotId in 0 .. endSlotId){
            val (key, value) = deleteData(slotId)
            resultKey[slotId] = key
            resultValue[slotId+1] = value
        }
        return resultKey to resultValue
    }

    override fun appendAllData(keys: List<ByteArray>, values: List<ByteArray>) {
        for(slotId in keys.indices){
            insertData(recordCount + slotId, keys[slotId], values[slotId])
        }
    }

    /**
     * Split keys into 2 pieces.
     * [[promotionKeyIdx]+1, len-1]
     *
     * @param promotionKeyIdx Standard for splitting values
     * @return split key array
     * */
    private fun splitData(promotionKeyIdx: Int): Pair<MutableList<ByteArray>, MutableList<ByteArray>>{
        val keyList = mutableListOf<ByteArray>()
        val childPageIdList = mutableListOf<ByteArray>()
        val totalRecordCount = recordCount
        for(slotId in promotionKeyIdx + 1 until totalRecordCount){
            val (key, value) = deleteData(slotId)
            keyList.add(key)
            childPageIdList.add(value)
        }
        return keyList to childPageIdList
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
    override fun redistribute(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int){
        // Borrow from right sibling
        if(isLeft(targetNode.pageId, parentNode, keyIdx)){
            val removedParentKey = parentNode.getData(keyIdx).first
            val (siblingKey, siblingValue) = targetNode.deleteData(0)
            val siblingLeftMostChild = targetNode.leftMostChildPageId
            insertData(recordCount, removedParentKey, valueSerializer.serialize(siblingLeftMostChild))
            parentNode.updateKey(keyIdx, siblingKey)
            targetNode.shiftSlot(0, targetNode.recordCount, -1)
            targetNode.leftMostChildPageId = valueSerializer.deserialize(siblingValue)
        } else{
            shiftSlot(0, recordCount, 1)
            val leftMostChild = valueSerializer.serialize(leftMostChildPageId)
            val removedParentKey = parentNode.getData(keyIdx-1).first
            val (siblingKey, siblingValue) = targetNode.deleteData(targetNode.recordCount - 1)
            insertData(0, removedParentKey, leftMostChild)
            leftMostChildPageId = valueSerializer.deserialize(siblingValue)
            parentNode.updateKey(keyIdx-1, siblingKey)
        }
    }

    /**
     * Merge right node into left one.
     *
     * @see Node.merge
     * */
    override fun merge(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int) {
        orderNode(targetNode, parentNode, keyIdx).let {
            (separationKeyIdx, lNode, rNode) ->
            val separationKey = parentNode.deleteData(separationKeyIdx).first
            val leftNode = lNode as InternalNode<K>
            val rightNode = rNode as InternalNode<K>
            val (rKey, rValue) = rightNode.deleteAllData()
            logger.info { "Merge Internal: leftNode: ${leftNode.hashCode()} rightNode: ${rightNode.hashCode()}" }
            rKey.addFirst(separationKey)
            lNode.appendAllData(rKey, rValue)
        }
    }
}