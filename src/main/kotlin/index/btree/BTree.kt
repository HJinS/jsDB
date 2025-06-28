package index.btree

import index.util.KeySchema
import index.util.KeyTool
import index.util.MAX_KEYS
import index.util.comparePackedKey
import java.util.EmptyStackException
import java.util.Stack
import kotlin.math.ceil
import kotlin.math.floor


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
     * - 부모의 자식 pointer update, promote key 찾아서 부모 노드로 승진
     * - 부모 노드는 stack 을 이용한 경로 추적
     * */
    fun insert(key: List<Any>) {
        val packedKey: ByteArray = KeyTool.pack(key, schema)
        root?.let {
            val leafNode = search(it, packedKey)
            leafNode.insert(packedKey, key, comparator)
            if(leafNode.isOverflow()){

            }
            traceNode.clear()
            // check overflow and do split
        } ?: LeafNode(mutableListOf(packedKey), maxKeys, mutableListOf(key))
    }

    fun split(){
        // rootNode 를 split 해야하는 경우 생각해야함
        while(traceNode.isEmpty()){
            // parentNode 의 parentNodeIdx 정보는 나중에 parentNode split 할 때 필요함 -> peek 사용
            val (currentNode, currentNodeIdx) = try {traceNode.pop()} catch (_: EmptyStackException) { throw IllegalStateException("Unexpected node trace data invalid")}
            var (parentNode, _) = try {traceNode.peek()} catch (_: EmptyStackException) { throw IllegalStateException("Unexpected node trace data invalid")}
            parentNode = parentNode as InternalNode
            val promotionKey = currentNode.promotionKey()
            val newNode = when(currentNode){
                is LeafNode -> {
                    val (splitKeys, splitValues) = currentNode.split()
                    val newNodeTemp = LeafNode(splitKeys, maxKeys, splitValues)
                    currentNode.linkNewSiblingNode(newNodeTemp)
                    newNodeTemp
                }
                is InternalNode -> {
                    val (splitKeys, splitChildren) = currentNode.split()
                    InternalNode(splitKeys, maxKeys, splitChildren)
                }
            }

            // parent key, value 삽입(leafNodeIdx 바로 오른쪽에)
            parentNode.updateNode(currentNodeIdx+1, promotionKey, newNode)
        }
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