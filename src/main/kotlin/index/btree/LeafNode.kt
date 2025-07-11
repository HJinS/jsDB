package index.btree

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

    fun insert(key: ByteArray, originalData: List<Any?>, comparator: Comparator<ByteArray>){
        val idx = search(key, comparator)
        keys.add(idx, key)
        _values.add(idx, originalData)
    }

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

    // split 으로 생긴 오른쪽의 새로 생긴 노드를 연결
    fun linkNewSiblingNode(siblingNode: LeafNode){
        val nextTemp = _next
        siblingNode._next = nextTemp
        siblingNode._prev = this
        _next = siblingNode
    }
}