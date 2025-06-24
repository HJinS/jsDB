package index.btree


/**
 * Pi -> Ki <= key < Ki+1
 * */
class InternalNode(
    private val children: MutableList<Node>
): Node(false, mutableListOf()) {
    fun moveToChild(index: Int): Node = children[index]
}