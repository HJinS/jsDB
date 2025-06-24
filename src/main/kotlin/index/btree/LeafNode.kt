package index.btree

class LeafNode(
    private val values: MutableList<Any?>,
    private var next: LeafNode? = null,
    private var prev: LeafNode? = null
): Node(true, mutableListOf()){
    fun insert(key: ByteArray, originalData: List<Any?> , comparator: Comparator<ByteArray>){
        val idx = search(key, comparator)
        values.add(if(idx >= 0) idx else -(idx + 1), originalData)
    }
}