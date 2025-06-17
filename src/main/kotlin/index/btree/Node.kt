package index.btree

import index.util.MAX_KEYS

/**
* @param keys: 노드의 키
* */
sealed class Node(
    val isLeaf: Boolean,
    private val keys: MutableList<ByteArray>
){
    fun isFull(): Boolean = keys.size > MAX_KEYS
}
