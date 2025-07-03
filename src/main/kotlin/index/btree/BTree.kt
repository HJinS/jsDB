package index.btree

import index.util.KeySchema
import index.util.KeyTool
import index.util.MAX_KEYS
import index.util.comparePackedKey
import java.util.EmptyStackException
import java.util.Stack


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
            if(leafNode.isOverflow){
                split()
            }
            traceNode.clear()
            // check overflow and do split
        } ?: LeafNode(mutableListOf(packedKey), maxKeys, mutableListOf(key))
    }

    fun split(){
        while(traceNode.isEmpty()){
            val (currentNode, currentNodeIdx) = try {traceNode.pop()} catch (_: EmptyStackException) { throw IllegalStateException("Unexpected node trace data invalid")}
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
            var parentNode: InternalNode
            if(traceNode.isEmpty()){
                parentNode = InternalNode(keys = mutableListOf(promotionKey), maxKeys, mutableListOf(currentNode, newNode))
                root = parentNode
            } else {
                // parentNode 의 parentNodeIdx 정보는 나중에 parentNode split 할 때 필요함 -> peek 사용
                parentNode = traceNode.peek().first as InternalNode
                // parent key, value 삽입(leafNodeIdx 바로 오른쪽에)
                parentNode.insert(currentNodeIdx+1, promotionKey, newNode)
            }
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

    fun traverse(): List<Pair<List<Any?>, List<Any?>>>{
        val result = mutableListOf<Pair<List<Any?>, List<Any?>>>()
        var currentNode: Node? = findLeftMostLeaf() ?: throw java.lang.IllegalStateException("Empty tree")
        while(currentNode != null){
            currentNode = currentNode as LeafNode
            val keys = currentNode.keyView
            for(i in keys.indices){
                val key: List<Any?> = KeyTool.unpack(keys[i], schema)
                val value: List<Any?> = currentNode.values[i]
                result += key to value
            }
            currentNode = currentNode.next
        }
        return result
    }

    fun findLeftMostLeaf(): LeafNode?{
        var currentNode = root ?: return null
        while(true){
            when(currentNode){
                is InternalNode -> {
                    currentNode = currentNode.moveToChild(0)
                }
                is LeafNode -> return currentNode
            }
        }
    }
}