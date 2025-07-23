package index.btree

import index.util.KeySchema
import index.util.comparePackedKey
import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor

/**
 * @param isLeaf: leaf 노드 여부
 * @param keys: 노드의 키
 * @param maxKeys: 노드가 가질 수 있는 최대 key 개수
* */
sealed class Node(
    val isLeaf: Boolean,
    internal val keys: MutableList<ByteArray>,
    internal val maxKeys: Int
){

    val keyView: List<ByteArray>
        get() = Collections.unmodifiableList(keys)

    val isOverflow: Boolean
        get() = keys.size > maxKeys

    val isUnderflow: Boolean
        get() = keys.size < ceil(maxKeys / 2.0)

    val hasSurplusKey: Boolean
        get() = keys.size > ceil(maxKeys / 2.0)

    fun search(key: ByteArray, comparator: Comparator<ByteArray>): Pair<Int, Boolean>{
        val idx = keys.binarySearch(key, comparator)
        logger.info { "Binary search result: $idx keyLength: ${keys.size}" }
        return if(idx >= 0) idx+1 to true else -(idx + 1) to false
    }

    fun isLeft(targetNode: Node, schema: KeySchema) = keys.last().comparePackedKey(targetNode.keys[0], schema) < 0

    fun removeLastKey() = keys.removeLast()

    fun removeFirstKey() = keys.removeFirst()

    abstract fun redistribute(targetNode: Node, parentNode: InternalNode, keyIdx: Int, schema: KeySchema)

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
     * @param schema Column data of the table or index.
     * */
    abstract fun merge(targetNode: Node, parentNode: InternalNode, keyIdx: Int, schema: KeySchema)

    fun promotionKeyIdx() = floor(keys.size.toDouble() / 2.0).toInt()
    fun promotionKey() = keys[promotionKeyIdx()]


    /**
     * Order node and return separationKey by the following rule.
     * Separation Key:
     * 1. keyIdx - 1 when merging with the left sibling.
     * 2. keyIdx when merging with the right sibling.
     *
     * @return Triple<separationKey, leftNode, rightNode>
     * */
    internal fun orderNode(targetNode: Node, keyIdx: Int, schema: KeySchema): Triple<Int, Node, Node> = if(isLeft(targetNode, schema)) {
        Triple(keyIdx, this, targetNode)
    } else {
        Triple(keyIdx-1, targetNode, this)
    }
}