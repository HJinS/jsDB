package index.btree

class InternalNode(
    private val children: MutableList<Node>
): Node(false, mutableListOf())