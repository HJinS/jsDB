package index.btree

class LeafNode(
    private val values: MutableList<Any?>,
    private var next: LeafNode? = null,
    private var prev: LeafNode? = null
): Node(true, mutableListOf()){
    fun insert(key: ByteArray, originalData: List<Any?> , comparator: Comparator<ByteArray>){
        val idx = insert(key, comparator)
        values.add(idx, originalData)
    }
}