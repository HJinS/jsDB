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

    fun insert(key: ByteArray, comparator: Comparator<ByteArray>){
        val idx = search(key, comparator)
        keys.add(idx, key)
    }

    fun search(key: ByteArray, comparator: Comparator<ByteArray>): Int{
        val idx = keys.binarySearch(key, comparator)
        return if(idx >= 0) idx else -(idx + 1)
    }
}