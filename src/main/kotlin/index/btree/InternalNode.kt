package index.btree


/**
 * Pi -> Ki <= key < Ki+1
 * */
class InternalNode(
    keys: MutableList<ByteArray>,
    maxKeys: Int,
    private val children: MutableList<Node>
): Node(false, keys, maxKeys) {
    fun moveToChild(index: Int): Node = children[index]
}