package index.btree

import index.util.KeySchema
import index.util.KeyTool
import index.util.MAX_KEYS
import index.util.comparePackedKey
import java.util.EmptyStackException
import java.util.Stack
import kotlin.math.ceil


/**
 * 모든 데이터는 leaf node 에만 저장
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
    private val traceNode: Stack<Pair<Node, Int>> = Stack()

    /**
     * 1. 삽입할 위치의 노드를 찾음
     * 2. 노드에 key, value 삽입
     * 3. split 이 필요한 경우에는 split 진행
     *  - leaf split 시에는 linked list 관리 필요
     *  - root split 시에는 높이 증가
     *
     * split
     *  - 노드 둘로 쪼개기(0 ~ upper(len/2), upper(len/2)+1 ~ (len-1))
     *  - 부모의 자식 pointer update, promote key 찾아서 부모 노드로 승진
     *  - promote key도 자식 노드에 그대로 남음(leaf 노드에서 split 한 경우)
     *  - internal node 에서 split 한 경우에는 다름
     *  - 부모 노드는 stack을 이용한 경로 추적
     * */
    fun insert(key: List<Any>) {
        val packedKey: ByteArray = KeyTool.pack(key, schema)
        root?.let {
            val leafNode = search(it, packedKey)
            leafNode.insert(packedKey, key, comparator)
            if(leafNode.isOverflow()){
                val (currentNode, currentNodeIdx) = try {
                    traceNode.pop()
                } catch (_: EmptyStackException) {
                    throw IllegalStateException("Unexpected node trace data invalid")
                }
                // parentNode의 parentNodeIdx 정보는 나중에 parentNode split 할 때 필요함
                val (parentNode, parentNodeIdx) = try {
                    traceNode.peek()
                } catch (_: EmptyStackException) {
                    throw IllegalStateException("Unexpected node trace data invalid")
                }

                val sizeOfCurrentNodeKey = currentNode.keys.size
                val dividePoint: Int = ceil(sizeOfCurrentNodeKey.toDouble() / 2.0).toInt() + 1
                val splitedCurrentNodeKeys = currentNode.keys.takeLast((sizeOfCurrentNodeKey -1) - dividePoint + 1)
                // value split
                // parent key, value 삽입(currentNodeIdx 바로 옆에)

                currentNode.keys.subList(dividePoint, sizeOfCurrentNodeKey).clear()



                // do some split
            }
            traceNode.clear()
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
     *
     * 경로를 보관할 stack이 필요
    * */
    fun search(start: Node, key: ByteArray): LeafNode {
        var node: Node = start
        traceNode.push(node to -1)
        if(node.isLeaf) return node as LeafNode
        do {
            val searchResult = start.search(key, comparator)
            node = node as InternalNode
            node = node.moveToChild(searchResult)
            traceNode.push(node to searchResult)
        } while(!node.isLeaf)
        return node as LeafNode
    }
}