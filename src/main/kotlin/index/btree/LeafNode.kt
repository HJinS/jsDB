package index.btree

class LeafNode(
    keys: MutableList<ByteArray>,
    maxKeys: Int,
    private val values: MutableList<Any?>,
    private var next: LeafNode? = null,
    private var prev: LeafNode? = null
): Node(true, keys, maxKeys){
    fun insert(key: ByteArray, originalData: List<Any?> , comparator: Comparator<ByteArray>){
        val idx = search(key, comparator)
        keys.add(idx, key)
        values.add(idx, originalData)
    }
}