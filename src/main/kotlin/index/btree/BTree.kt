package index.btree

import index.util.KeySchema
import index.util.KeyTool
import index.util.comparePackedKey


/**
 * 모든 데이터는 leaf node에만 저장
 * 정렬된 키 배열을 유지
 * 내부 노드는 분기를 위해 존재
 * 키 개수는 최대 MAX_DEGREE 만큼만
 *
* */
class BTree (val name: String, val targetTable: String, private val schema: KeySchema){
    private val comparator = Comparator<ByteArray> {
        a, b -> a.comparePackedKey(b, schema)
    }

    fun <T> insert(key: List<Any>) = 0

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