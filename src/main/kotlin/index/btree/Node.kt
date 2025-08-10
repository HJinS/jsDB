package index.btree

import java.util.Collections
import kotlin.math.floor

/**
 * @param isLeaf: leaf 노드 여부
 * @param keys: 노드의 키
 * @param maxKeys: 노드가 가질 수 있는 최대 key 개수
* */
sealed class Node(
    val isLeaf: Boolean,
    internal val keys: MutableList<ByteArray>,
    private val maxKeys: Int
){

    val keyView: List<ByteArray>
        get() = Collections.unmodifiableList(keys)

    val isOverflow: Boolean
        get() = keys.size > maxKeys

    val isUnderflow: Boolean
        get() = keys.size < maxKeys / 2

    val hasSurplusKey: Boolean
        get() = keys.size > maxKeys / 2

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
    fun search(key: ByteArray, comparator: Comparator<ByteArray>, exactIndex: Boolean=false): Pair<Int, Boolean>{
        val idx = keys.binarySearch(key, comparator)
        logger.info { "Binary search result: $idx keyLength: ${keys.size}" }
        return if(idx >= 0) {if(exactIndex) idx to true else idx+1 to true} else -(idx + 1) to false
    }

    fun isLeft(targetNode: Node, parentNode: InternalNode, keyIdx: Int): Boolean{
        return try {
            val rightChild = parentNode.moveToChild(keyIdx+1)
            targetNode === rightChild
        } catch (_: IndexOutOfBoundsException){
            val leftChild = parentNode.moveToChild(keyIdx-1)
            targetNode !== leftChild
        }
    }

    fun removeLastKey(): ByteArray = keys.removeLast()

    fun removeFirstKey(): ByteArray = keys.removeFirst()

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
     * */
    abstract fun merge(targetNode: Node, parentNode: InternalNode, keyIdx: Int)

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
    internal fun orderNode(targetNode: Node, parentNode: InternalNode, keyIdx: Int): Triple<Int, Node, Node> = if(isLeft(targetNode, parentNode, keyIdx)) {
        Triple(keyIdx, this, targetNode)
    } else {
        Triple(keyIdx-1, targetNode, this)
    }
}