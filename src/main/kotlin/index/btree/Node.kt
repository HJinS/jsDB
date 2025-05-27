package index.btree

/**
* @param t: max degree(최대 자식 수)
* @pra
*
* max key -> t - 1
* */
abstract class Node(
    private val t: Int
){
    private var keys: MutableList<ByteArray> = mutableListOf()
        private set
    fun isFull(): Boolean = keys.size > (t - 1)
}
