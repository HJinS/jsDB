package index.btree

import index.util.KeySchema
import index.util.KeyTool
import index.util.MAX_KEYS
import index.util.comparePackedKey


/**
 * 모든 데이터는 leaf node에만 저장
 * 정렬된 키 배열을 유지
 * 내부 노드는 분기를 위해 존재
 * 키 개수는 최대 MAX_DEGREE 만큼만
 *
* */
class BTree (
    val name: String,
    val targetTable: String,
    private val schema: KeySchema,
    private val maxKeys: Int = MAX_KEYS,
    private val allowDuplicate: Boolean = true
){
    private var root: Node? = null
    private val comparator = Comparator<ByteArray> {
        a, b -> a.comparePackedKey(b, schema)
    }

    /**
     * 1. 삽입할 위치의 노드를 찾음
     * 2. 노드에 key, value 삽입
     * 3. split 이 필요한 경우에는 split 진행
     *  - leaf split 시에는 linked list 관리 필요
     *  - root split 시에는 높이 증가
     * */
    fun insert(key: List<Any>) {
        val packedKey: ByteArray = KeyTool.pack(key, schema)
        root?.let {
            val leafNode = search(it, packedKey)
            leafNode.insert(packedKey, key, comparator)
            // check overflow and do split
        } ?: LeafNode(mutableListOf(packedKey), maxKeys, mutableListOf(key))
    }

    /**
     * find node by keyIndex, value.
     * p1: k1 <= key < k2
     * p2: k2 <= key < k3
     * key <= nodeKey 인 첫 nodeKey 를 찾고 그 왼쪽 자식으로 간다
     * 즉 searchKey 가 노드의 key 보다 작거나 같아야 한다.
     * 왼쪽으로 가야(더 작은범위부터 찾아야) 모든 범위를 찾을 수 있다.
     * 1, 5, 10 -> 3을 찾을 경우 5의 idx가 필요함
     * 1, 5, 10 -> 5를 찾을 경우 5의 idx가 필요함
    * */
    fun search(start: Node, key: ByteArray): LeafNode {
        var node: Node = start
        if(node.isLeaf) return node as LeafNode
        do {
            val searchResult = start.search(key, comparator)
            node = node as InternalNode
            node = node.moveToChild(searchResult)
        } while(!node.isLeaf)
        return node as LeafNode
    }
}