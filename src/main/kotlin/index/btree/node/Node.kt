package index.btree.node

import config.PageConfig
import index.serializer.BaseKeySerializer
import index.serializer.ValueSerializer
import storageEngine.exception.SlottedPageException
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
import java.nio.ByteBuffer
import kotlin.math.floor

abstract class Node<K>(
    pageConfig: PageConfig,
    pageId: Long = -1,
    data: ByteBuffer,
    pageType: PageType,
    protected val keySerializer: BaseKeySerializer<K>
): SlottedPage(pageConfig, pageId, data, pageType) {

    val keyView: List<ByteArray>
        get() = object : AbstractList<ByteArray>() {
            override val size: Int
                get() = recordCount

            override fun get(index: Int): ByteArray {
                return getData(index).first
            }
        }

    val isOverflow: Boolean
        get() = recordCount > pageConfig.maxKeys

    val isUnderflow: Boolean
        get() = recordCount < pageConfig.maxKeys / 2

    val hasSurplusKey: Boolean
        get() = recordCount > pageConfig.maxKeys / 2

    /**
     * Find value or child pointer using key.
     *
     * If find the path to the leaf node, go down to idx+1 (if exists).
     * - If the key exists, corresponding children are in right subtree.
     *
     * If key doesn't exist, then goes to (-(idx+1)).
     * - Kotlin's binary search return inverted insertion point.
     *
     * If find the exact value using the key at leaf node, use idx (if exists).
     *
     * @param key Key to find.
     * @param comparator Comparator to compare at the binary search.
     * @param exactIndex Use this parameter to get the exact node from the leaf node.
     * @return Search result. Pair of index, isExist.
     * */
    fun search(key: ByteArray, exactIndex: Boolean=false): Pair<Int, Boolean>{
        val idx = binarySearch(key)
        index.btree.logger.info { "Binary search result: $idx keyLength: $recordCount" }
        return if(idx >= 0) {if(exactIndex) idx to true else idx+1 to true} else -(idx + 1) to false
    }

    fun isLeft(targetPageId: Long, parentNode: InternalNode<K>, keyIdx: Int): Boolean{
        return try {
            val rightChildId = parentNode.childPageId(keyIdx + 1)
            targetPageId == rightChildId
        } catch (_: SlottedPageException.SlotOutOfBoundException){
            val leftChildId = parentNode.childPageId(keyIdx-1)
            targetPageId != leftChildId
        }
    }

    abstract fun redistribute(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int)

    /**
     * Merge the right node into the left node.
     * - Separation Key should be removed.
     * - [InternalNode] Separation Key should be added to the left node.
     * - [InternalNode] All keys, children from the right node should be added to the left node.
     * - [LeafNode] All keys, values from the right node should be added to the left node.
     * - [LeafNode] Should reconnect the left, right node's link.
     * - Parent's child pointer of separationKey + 1 should be removed.
     *
     * Separation Key:
     * - keyIdx - 1 when merging with the left sibling.
     * - keyIdx when merging with the right sibling.
     *
     * @param targetNode One of my sibling nodes.
     * @param parentNode My parent node. It should be the internal node.
     * @param keyIdx Index which I used to get to the leaf node.
     * */
    abstract fun merge(targetNode: Node<K, V>, parentNode: InternalNode<K>, keyIdx: Int)

    fun promotionKeyIdx() = floor(recordCount.toDouble() / 2.0).toInt()
    fun promotionKey() = getData(promotionKeyIdx()).first


    /**
     * Order node and return separationKey by the following rule.
     * Separation Key:
     * 1. keyIdx - 1 when merging with the left sibling.
     * 2. keyIdx when merging with the right sibling.
     *
     * @return Triple<separationKey, leftNode, rightNode>
     * */
    internal fun orderNode(
        targetNode: Node<K>,
        parentNode: InternalNode<K>,
        keyIdx: Int
    ): Triple<Int, Node<K>, Node<K>> = if(isLeft(targetNode.pageId, parentNode, keyIdx)) {
        Triple(keyIdx, this, targetNode)
    } else {
        Triple(keyIdx-1, targetNode, this)
    }
}