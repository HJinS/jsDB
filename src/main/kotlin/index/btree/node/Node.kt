package index.btree.node

import config.IndexConfig
import exception.IndexException
import util.EngineErrorDetail
import index.btree.BTreeOptMode
import exception.StorageEngineException
import storageEngine.page.SlottedPage
import util.PageType
import kotlin.math.floor

abstract class Node(
    val indexConfig: IndexConfig,
    val page: SlottedPage,
){

    companion object {
        fun from(
            indexConfig: IndexConfig,
            page: SlottedPage
        ): Node{
            return when(page.type){
                PageType.LEAF_NODE -> LeafNode(indexConfig, page)
                PageType.INTERNAL_NODE -> InternalNode(indexConfig, page)
                else -> throw IndexException.InvalidNodeType(
                    EngineErrorDetail(
                        pageType = page.type,
                        reason = "Invalid node type"
                    )
                )
            }
        }
    }

    val keyView: List<ByteArray>
        get() = object : AbstractList<ByteArray>() {
            override val size: Int
                get() = page.recordCount

            override fun get(index: Int): ByteArray {
                return page.getData(index).first
            }
        }

    open val valueView: List<ByteArray>
        get() = object : AbstractList<ByteArray>() {
            override val size: Int
                get() = page.recordCount

            override fun get(index: Int): ByteArray {
                return page.getData(index).second
            }
        }

    val isLeaf: Boolean
        get() = page.type == PageType.LEAF_NODE

    val isOverflow: Boolean
        get() = page.recordCount > indexConfig.maxKeys

    val isUnderflow: Boolean
        get() = page.recordCount < indexConfig.maxKeys / 2

    val hasSurplusKey: Boolean
        get() = page.recordCount > indexConfig.maxKeys / 2

    val pageId: Long
        get() = page.pageId

    val keyCount: Int
        get() = page.recordCount

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
     * @param exactIndex Use this parameter to get the exact node from the leaf node.
     * @return Search result. Pair of index, isExist.
     * */
    fun search(key: ByteArray, exactIndex: Boolean=false): Pair<Int, Boolean>{
        val idx = page.binarySearch(key)
        return if(idx >= 0) {if(exactIndex) idx to true else idx+1 to true} else -(idx + 1) to false
    }

    fun isLeft(targetPageId: Long, parentNode: InternalNode, keyIdx: Int): Boolean{
        return try {
            val rightChildId = parentNode.childPageId(keyIdx + 1)
            targetPageId == rightChildId
        } catch (_: StorageEngineException.SlotOutOfBound){
            val leftChildId = parentNode.childPageId(keyIdx-1)
            targetPageId != leftChildId
        }
    }

    fun insert(key: ByteArray, value: ByteArray){
        val insertSlotId = search(key).first
        page.insertData(insertSlotId, key, value)
    }

    fun insertAt(slotId: Int, key: ByteArray, data: ByteArray){
        page.insertData(slotId, key, data)
    }

    fun deleteData(slotId: Int) = page.deleteData(slotId)

    abstract fun redistribute(targetNode: Node, parentNode: InternalNode, keyIdx: Int)

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
     * @return Page ID pair of left, right node.
     * */
    abstract fun merge(targetNode: Node, parentNode: InternalNode, keyIdx: Int): Pair<Long, Long>

    abstract fun deleteAllData(): Pair<List<ByteArray>, List<ByteArray>>

    abstract fun appendAllData(keys: List<ByteArray>, values: List<ByteArray>)

    fun promotionKeyIdx() = floor(page.recordCount.toDouble() / 2.0).toInt()


    /**
     * Order node and return separationKey by the following rule.
     * Separation Key:
     * 1. keyIdx - 1 when merging with the left sibling.
     * 2. keyIdx when merging with the right sibling.
     *
     * @return Triple<separationKey, leftNode, rightNode>
     * */
    internal fun orderNode(
        targetNode: Node,
        parentNode: InternalNode,
        keyIdx: Int
    ): Triple<Int, Node, Node> = if(isLeft(targetNode.page.pageId, parentNode, keyIdx)) {
        Triple(keyIdx, this, targetNode)
    } else {
        Triple(keyIdx-1, targetNode, this)
    }

    /**
     * Whether it's safe, for latch crabbing, to release the ancestor locks/pins already held once
     * the descent has reached this node for the given [optMode] — i.e. whether the actual
     * operation at this node is guaranteed not to trigger a split/merge that propagates upward.
     * - INSERT: safe only if this node has both a free key slot and enough physical space for
     *   [key]/[value] ([wouldOverflow]) — otherwise a split here could propagate to the parent.
     * - DELETE: safe if this node has more than the minimum key count ([hasSurplusKey]) —
     *   otherwise a merge/redistribute here could propagate to the parent.
     * - UPDATE: currently reuses the DELETE condition ([hasSurplusKey]) only — it does **not**
     *   check whether the new (possibly larger) value could overflow this node, so ancestor locks
     *   can be released even when the update's re-insert would need to split. See issue #59.
     * - SELECT: always safe — reads never trigger structural changes.
     * */
    fun isSafeNode(optMode: BTreeOptMode, key: ByteArray?=null, value: ByteArray?=null) = when(optMode){
        BTreeOptMode.INSERT -> {
            if(!(key != null && value != null))
                throw IndexException.InvalidSafeCheck(
                    EngineErrorDetail(
                        reason = "Key, Value must be provided for safe check when optMode is Insert or Update"
                    )
                )
            keyCount < indexConfig.maxKeys && !wouldOverflow(key, value)
        }
        BTreeOptMode.DELETE -> hasSurplusKey
        BTreeOptMode.UPDATE -> hasSurplusKey
        BTreeOptMode.SELECT -> true
    }

    fun wouldOverflow(key: ByteArray, value: ByteArray) =
        page.freeSpace < page.getRequiredSpace(key, value) || keyCount >= indexConfig.maxKeys
}
