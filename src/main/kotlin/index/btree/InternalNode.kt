package index.btree

class InternalNode(private val t: Int): Node(t) {
    var pChild: MutableList<Node> = mutableListOf();
}