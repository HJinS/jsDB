package index.btree

import index.util.KeySchema
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
        get() = keys.size < maxKeys / 2

    val hasSurplusKey: Boolean
        get() = keys.size > maxKeys / 2

    /**
     * 어디로 내려가야할 지 경로를 찾을 경우에는 실제로 값이 있는 경우 idx+1, 삭제를 위해 찾는 경우는 idx 필요
     * 음수인 경우는 insertionPoint 그대로 사용(-(idx+1))
     * internode 에서 따라 내려가는 경우에는 idx+1로 내려가야한다. 같은 케이스는 오른쪽 subtree 에 있기 때문
     * leafNode 안에서 정확한 key 값의 위치를 찾기 위해서는 idx 를 그대로 쓰면 된다.
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
    internal fun orderNode(targetNode: Node, parentNode: InternalNode, keyIdx: Int): Triple<Int, Node, Node> = if(isLeft(targetNode, parentNode, keyIdx)) {
        Triple(keyIdx, this, targetNode)
    } else {
        Triple(keyIdx-1, targetNode, this)
    }
}