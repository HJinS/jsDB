package btree

/**
* @param t: minimum degree(최소 차수)
* @pra
*
* minimum key -> t - 1
* maximum key -> 2t -1
* minimum child -> t
* maximum child -> 2t
* */
class Node<T: Comparable<T>>(
    val t: Int
    var isLeaf: Boolean
){
    var keys: MutableList<T> = mutableListOf()
    var pChild: MutableList<Node<T>> = mutableListOf()
    fun isFull(): Boolean = keys.size == (2 * t - 1)

}
