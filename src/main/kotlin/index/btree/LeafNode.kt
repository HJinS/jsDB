package index.btree

import index.util.KeySchema
import java.util.Collections

class LeafNode(
    keys: MutableList<ByteArray>,
    maxKeys: Int,
    values: MutableList<List<Any?>>,
    next: LeafNode? = null,
    prev: LeafNode? = null
): Node(true, keys, maxKeys){

    private var _next = next
    private var _prev = prev
    private val _values = values

    val next: LeafNode?
        get() = _next

    val prev: LeafNode?
        get() = _prev

    val values: List<List<Any?>>
        get() = Collections.unmodifiableList(_values)

    fun insert(idx: Int, key: ByteArray, originalData: List<Any?>){
        keys.add(idx, key)
        _values.add(idx, originalData)
    }

    fun delete(keyIdx: Int){
        keys.removeAt(keyIdx)
        _values.removeAt(keyIdx)
    }

    fun removeFirstValue() = _values.removeFirst()

    fun removeLastValue() = _values.removeLast()

    /**
     * leaf node
     * - 추가로 value split 필요
     * - key < maxKeys 인 경우 split
     * - mid(promote key) 값을 floor(len / 2)로 지정
     * - 0 ~ mid-1, mid ~ len-1 좌우 분리(닫힌 구간)
     * - promote key 도 leaf node 에 남아야함
     * */
    fun split(): Pair<MutableList<ByteArray>, MutableList<List<Any?>>> {
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys = splitKey()
        val splitValues = splitValues(promotionKeyIdx)
        return splitKeys to splitValues
    }

    private fun splitKey(): MutableList<ByteArray>{
        val keySize = keys.size
        val promotionKeyIdx = promotionKeyIdx()
        val splitKeys =  keys.takeLast(keySize - promotionKeyIdx).toMutableList()
        keys.subList(promotionKeyIdx, keySize).clear()
        return splitKeys
    }

    private fun splitValues(promotionKeyIdx: Int): MutableList<List<Any?>>{
        val valueSize = _values.size
        val splitValues =  _values.takeLast(valueSize - promotionKeyIdx).toMutableList()
        _values.subList(promotionKeyIdx, valueSize).clear()
        return splitValues
    }

    /**
     * Link new node.
     * */
    fun linkNewSiblingNode(siblingNode: LeafNode){
        val nextTemp = _next
        siblingNode._next = nextTemp
        siblingNode._prev = this
        _next = siblingNode
    }

    /**
     * ### Leaf redistribution
     * #### borrow from the left sibling
     * separateKey - (keyIdx - 1)
     * 1. Take the biggest key of the left sibling.
     * 2. Take a right most value of the left sibling.
     * 3. Insert key and value to me as the smallest one
     * 4. Update the parent node's separator keys to the key of 3.
     *
     * #### borrow from the right sibling
     * separateKey - (keyIdx)
     * 1. Take the smallest key of the right sibling.
     * 2. Take the left most value of the right sibling.
     * 3. Insert key and value to me as the biggest one.
     * 4. Update the parent node's separator keys to the new smallest key of the right sibling
     * */
    override fun redistribute(targetNode: Node, parentNode: InternalNode, keyIdx: Int, schema: KeySchema){
        val node: LeafNode = targetNode as LeafNode
        // borrow from right sibling
        if(isLeft(node, schema)){
            val key = node.removeFirstKey()
            val value = node.removeFirstValue()
            node.keys.addLast(key)
            node._values.addLast(value)
            parentNode.keys[keyIdx] = node.keys[0]
        } else{
            val key = node.removeLastKey()
            val value = node.removeLastValue()
            node.keys.addFirst(key)
            node._values.addFirst(value)
            parentNode.keys[keyIdx-1] = key
        }
    }

    override fun merge(targetNode: Node, parentNode: InternalNode, keyIdx: Int, schema: KeySchema) {
        val node: LeafNode = targetNode as LeafNode
        val leftNode: LeafNode
        val rightNode: LeafNode
        orderNode(node, keyIdx, schema).let {
            (separationKey, lNode, rNode) ->
            leftNode = lNode as LeafNode
            rightNode = rNode as LeafNode
            val extractedKey = rightNode.keys
            val newNextNode = rightNode.next
            leftNode.keys.addAll(extractedKey)
            leftNode._values.addAll(rightNode.values)
            parentNode.keys.removeAt(separationKey)
            parentNode.removeChildrenAt(separationKey+1)

            leftNode._next = newNextNode
            newNextNode?._prev = leftNode
        }
    }
}