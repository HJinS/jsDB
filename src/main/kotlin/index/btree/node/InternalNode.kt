package index.btree.node

import config.IndexConfig
import index.serializer.KeySerializer
import index.serializer.PageIDSerializer
import index.util.NodeSplitData
import storageEngine.page.SlottedPage


class InternalNode<K>(
    indexConfig: IndexConfig,
    page: SlottedPage,
    keySerializer: KeySerializer<K>,
    private val valueSerializer: PageIDSerializer = PageIDSerializer()
): Node<K>(indexConfig, page, keySerializer) {

    override val valueView: List<ByteArray>
        get() = object : AbstractList<ByteArray>() {
            override val size: Int
                get() = page.recordCount + 1

            override fun get(index: Int): ByteArray {
                return if(index == 0) valueSerializer.serialize(page.leftMostChildPageId)
                else page.getData(index - 1).second
            }
        }

    fun childPageId(index: Int): Long = if(index == 0) page.leftMostChildPageId else valueSerializer.deserialize(page.getData(index - 1).second)

    fun updateKey(slotId: Int, key: ByteArray){
        val (_, value) = page.getData(slotId)
        page.updateData(slotId, key, value)
    }

    /**
     * Split the internal node into 2 pieces.
     * - PromotionKey: floor(len / 2)
     * - PromotionKey goes to parent node.
     * - len: total count of key.
     * - Key separation: [0, promotionKey-1], [promotionKey+1, len-1]
     * - Children separation: [0, promotionKey], [promotionKey+1, len]
     *
     * @see splitData
     * @see NodeSplitData
     * @see promotionKeyIdx
     * @return key, value for a new node.
     * */
    fun split(): NodeSplitData {
        val promotionKeyIdx = promotionKeyIdx()
        val (promotionKey, leftMostChildPageId) = page.getData(promotionKeyIdx)
        val leftMostChildPageIdDecoded = valueSerializer.deserialize(leftMostChildPageId)
        val (splitKeyList, splitChildrenId) = splitData(promotionKeyIdx)
        page.deleteData(promotionKeyIdx)
        return NodeSplitData(
            splitKeyList, splitChildrenId, promotionKey, leftMostChildPageIdDecoded
        )
    }

    override fun deleteAllData(): Pair<MutableList<ByteArray>, MutableList<ByteArray>>{
        val endSlotId = page.recordCount - 1
        val resultKey = mutableListOf<ByteArray>()
        val resultValue = mutableListOf<ByteArray>()
        val leftMostChildPageIdSerialized = valueSerializer.serialize(page.leftMostChildPageId)
        for(slotId in endSlotId downTo 0){
            val (key, value) = page.deleteData(slotId)
            resultKey.addFirst(key)
            resultValue.addFirst(value)
       }
        resultValue.addFirst(leftMostChildPageIdSerialized)
        return resultKey to resultValue
    }

    override fun appendAllData(keys: List<ByteArray>, values: List<ByteArray>) {
        val startSlot = page.recordCount
        for(slotId in keys.indices){
            page.insertData(startSlot + slotId, keys[slotId], values[slotId])
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
        val totalRecordCount = page.recordCount
        for(slotId in totalRecordCount-1 downTo promotionKeyIdx+1){
            val (key, value) = page.deleteData(slotId)
            keyList.addFirst(key)
            childPageIdList.addFirst(value)
        }
        page.compaction()
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
        if(isLeft(targetNode.page.pageId, parentNode, keyIdx)){
            val removedParentKey = parentNode.page.getData(keyIdx).first
            val (siblingKey, siblingValue) = targetNode.page.deleteData(0)
            val siblingLeftMostChild = targetNode.page.leftMostChildPageId
            page.insertData(page.recordCount, removedParentKey, valueSerializer.serialize(siblingLeftMostChild))
            parentNode.updateKey(keyIdx, siblingKey)
            targetNode.page.leftMostChildPageId = valueSerializer.deserialize(siblingValue)
        } else{
            val leftMostChild = valueSerializer.serialize(page.leftMostChildPageId)
            val removedParentKey = parentNode.page.getData(keyIdx-1).first
            val (siblingKey, siblingValue) = targetNode.page.deleteData(targetNode.page.recordCount - 1)
            page.insertData(0, removedParentKey, leftMostChild)
            page.leftMostChildPageId = valueSerializer.deserialize(siblingValue)
            parentNode.updateKey(keyIdx-1, siblingKey)
        }
    }

    /**
     * Merge right node into left one.
     *
     * @see Node.merge
     * */
    override fun merge(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int): Pair<Long, Long> {
        orderNode(targetNode, parentNode, keyIdx).let {
            (separationKeyIdx, lNode, rNode) ->
            val separationKey = parentNode.page.deleteData(separationKeyIdx).first
            val leftNode = lNode as InternalNode<K>
            val rightNode = rNode as InternalNode<K>
            val (rKey, rValue) = rightNode.deleteAllData()
            rKey.addFirst(separationKey)
            leftNode.appendAllData(rKey, rValue)
            return leftNode.pageId to rightNode.pageId
        }
    }
}
