package index.btree

class LeafNode(
    internal val values: MutableList<Any?>,
    internal var next: LeafNode? = null,
    internal var prev: LeafNode? = null
): Node(true, mutableListOf())