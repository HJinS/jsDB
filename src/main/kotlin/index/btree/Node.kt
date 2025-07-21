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
        return if(idx >= 0) idx to true else -(idx + 1) to false
    }

    fun isLeft(targetNode: Node, schema: KeySchema) = keys.last().comparePackedKey(targetNode.keys[0], schema) < 0

    fun removeLastKey() = keys.removeLast()

    fun removeFirstKey() = keys.removeFirst()


    // 왼쪽에서 빌려오는 경우는 부모의 keyIdx-1 업데이트
    // 오른쪽에서 빌려오는 경우는 부모의 keyIdx 업데이트
    abstract fun redistribute(targetNode: Node, parentNode: InternalNode, keyIdx: Int, schema: KeySchema)

    fun promotionKeyIdx() = floor(keys.size.toDouble() / 2.0).toInt()
    fun promotionKey() = keys[promotionKeyIdx()]
}