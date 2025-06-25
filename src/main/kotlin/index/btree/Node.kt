package index.btree

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
    fun isFull(): Boolean = keys.size > maxKeys

    fun insert(key: ByteArray, comparator: Comparator<ByteArray>){
        val idx = search(key, comparator)
        keys.add(idx, key)
    }

    fun search(key: ByteArray, comparator: Comparator<ByteArray>): Int{
        val idx = keys.binarySearch(key, comparator)
        return if(idx >= 0) idx else -(idx + 1)
    }
}