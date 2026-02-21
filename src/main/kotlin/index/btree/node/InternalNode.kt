package index.btree.node

import config.PageConfig
import index.btree.logger
import index.serializer.BaseKeySerializer
import index.serializer.PageIDSerializer
import index.serializer.ValueSerializer
import index.util.NodeSplitData
import storageEngine.util.PageType
import java.nio.ByteBuffer


class InternalNode<K>(
    pageConfig: PageConfig,
    pageId: Long = -1,
    data: ByteBuffer,
    pageType: PageType = PageType.INTERNAL_NODE,
    keySerializer: BaseKeySerializer<K>,
    valueSerializer: ValueSerializer<Long> = PageIDSerializer()
): Node<K, Long>(pageConfig, pageId, data, pageType, keySerializer, valueSerializer) {

    val childCount: Int
        get() = recordCount + 1

    fun childPageId(index: Int): Long = if(index == 0) leftMostChildPageId else valueSerializer.deserialize(getData(index).second)

    fun insert(idx: Int, key: ByteArray, childNode: Node) {
        children.add(idx+1, childNode)
        keys.add(idx, key)
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