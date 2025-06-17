package index.btree

class InternalNode(
    internal val children: MutableList<Node>
): Node(false, mutableListOf())