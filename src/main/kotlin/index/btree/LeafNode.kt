package index.btree

class LeafNode(private val t: Int): Node(t) {
    var leftSibling: Node? = null
    var rightSibling: Node? = null
}