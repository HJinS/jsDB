package index.btree.node

import config.IndexConfig
import index.exception.NodeException
import index.serializer.KeySerializer
import index.util.BTreeOptMode
import storageEngine.exception.SlottedPageException
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
import kotlin.math.floor

abstract class Node<K>(
    val indexConfig: IndexConfig,
    val page: SlottedPage,
    protected val keySerializer: KeySerializer<K>
){

    companion object {
        fun <K> from(
            indexConfig: IndexConfig,
            page: SlottedPage,
            keySerializer: KeySerializer<K>
        ): Node<K>{
            return when(page.type){
                PageType.LEAF_NODE -> LeafNode(indexConfig, page, keySerializer)
                PageType.INTERNAL_NODE -> InternalNode(indexConfig, page, keySerializer)
                else -> throw NodeException.InvalidNodeTypeException(page.type)
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

    fun isLeft(targetPageId: Long, parentNode: InternalNode<K>, keyIdx: Int): Boolean{
        return try {
            val rightChildId = parentNode.childPageId(keyIdx + 1)
            targetPageId == rightChildId
        } catch (_: SlottedPageException.SlotOutOfBoundException){
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

    fun deleteAt(slotId: Int){
        page.deleteData(slotId)
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
     * @return Page ID pair of left, right node.
     * */
    abstract fun merge(targetNode: Node<K>, parentNode: InternalNode<K>, keyIdx: Int): Pair<Long, Long>

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
        targetNode: Node<K>,
        parentNode: InternalNode<K>,
        keyIdx: Int
    ): Triple<Int, Node<K>, Node<K>> = if(isLeft(targetNode.page.pageId, parentNode, keyIdx)) {
        Triple(keyIdx, this, targetNode)
    } else {
        Triple(keyIdx-1, targetNode, this)
    }

    /**
     * 특정 operation을 진행할 때 조상의 Lock 및 pin 을 풀어도 안전한지
     * INSERT -> 삽입 시에는 하나 더 넣어도 split이 일어나지 않아야 함(keyCount < maxKeyCount)
     * DELETE -> 삭제 시에는 하나 삭제하여도 underflow 상태가 되지 않아야 함 -> hasSurplusKey
     * 추후 INSERT시에 용량을 기준으로한 조건 추가 필요.
     * 그 경우에 UPDATE 시에는 UPDATE에 따른 가변 길이의 데이터 수정 시 용량 확인 필요
     * */
    fun isSafeNode(optMode: BTreeOptMode, key: ByteArray?=null, value: ByteArray?=null) = when(optMode){
        BTreeOptMode.INSERT -> {
            if(!(key != null && value != null)) throw NodeException.InvalidSafeCheckException()
            keyCount < indexConfig.maxKeys && !wouldOverflow(key, value)
        }
        BTreeOptMode.DELETE -> hasSurplusKey
        BTreeOptMode.UPDATE -> hasSurplusKey
        BTreeOptMode.SELECT -> true
    }

    fun wouldOverflow(key: ByteArray, value: ByteArray) = page.freeSpace < page.getRequiredSpace(key, value) || keyCount >= indexConfig.maxKeys
}
