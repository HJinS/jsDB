package index.btree

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

    fun promotionKeyIdx() = floor(keys.size.toDouble() / 2.0).toInt()
    fun promotionKey() = keys[promotionKeyIdx()]
}