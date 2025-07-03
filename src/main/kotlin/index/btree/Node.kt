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
    internal val maxKeys: Int
){

    val keyView: List<ByteArray>
        get() = Collections.unmodifiableList(keys)

    val isOverflow: Boolean
        get() = keys.size > maxKeys

    fun search(key: ByteArray, comparator: Comparator<ByteArray>): Int{
        val idx = keys.binarySearch(key, comparator)
        return if(idx >= 0) idx else -(idx + 1)
    }

    internal fun splitKey(): MutableList<ByteArray>{
        val keySize = keys.size
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys =  keys.takeLast(keySize - promotionKeyIdx - 1).toMutableList()
        keys.subList(promotionKeyIdx+1, keySize).clear()
        return splitKeys
    }

    fun promotionKeyIdx() = floor(keys.size.toDouble() / 2.0).toInt()
    fun promotionKey() = keys[promotionKeyIdx()]
}