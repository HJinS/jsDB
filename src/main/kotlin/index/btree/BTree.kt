package index.btree

import index.util.KeySchema
import index.util.KeyTool
import index.util.MAX_KEYS
import index.util.comparePackedKey
import mu.KotlinLogging
import java.util.EmptyStackException
import java.util.Stack
import kotlin.collections.plusAssign

val logger = KotlinLogging.logger {}
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
        logger.info { "insert key: $key"}
        root?.let {
            logger.info { "Root node is not null key: $key" }
            val (leafNode, idx, _) = search(packedKey)
            leafNode.insert(idx, packedKey, key)
            if(leafNode.isOverflow){
                logger.info { "split node" }
                try{
                    split()
                } catch (e: Exception) {
                    throw e
                }
            }
            traceNode.clear()
            // check overflow and do split
        } ?: run {
            logger.info { "Root node is null. Make new root node key: $key" }
            root = LeafNode(mutableListOf(packedKey), maxKeys, mutableListOf(key))
        }
        printTree()
    }


    /**
     * delete
     * key의 최소 개수는 ceil(maxKeys/2) 이상 이어야함.
     *
     * 1. 삭제할 key 를 찾아 leafNode 로 내려감
     * 2. key <= nodeKey 기준으로 검색(기준은 기존과 동일)
     * 3. underflow 검사
     * 4. Rebalancing
     *
     *     4-1. Redistribution
     *
     *         더 오른쪽 노드의 새로운 minKey 를 부모의 key 로 업데이트
     *         4-1-1. 왼쪽으로부터 가져오는 경우
     *             4-1-1-1. 왼쪽 형제 노드의 가장 큰 키를 가지고옴
     *             4-1-1-2. 기존의 구분키를 새로 가지고온 키로 업데이트
     *         4-1-2. 오른쪽으로부터 가져오는 경우
     *             4-1-2-1. 오른쪽 노드의 가장 작은 키를 가지고옴
     *             4-2-2-2. 기존의 구분키를 오른쪽 형제의 새로운 최소 키로 업데이트
     *
     *     4-2. Merge
     *
     *         오른쪽에 있는 노드가 왼쪽 노드에 합쳐짐
     *         4-2-1. 형제 노드와 합체
     *         4-2-2. 부모의 separator key 도 제거됨.
     *         4-2-3. 상위 노드까지 확인해서 게속적으로 rebalancing 필요.
     *
     * */
    fun delete(key: List<Any>){
        val packedKey: ByteArray = KeyTool.pack(key, schema)
        val (leafNode, keyIdx, isExist) = search(packedKey)
        if(isExist){
            leafNode.delete(keyIdx)
            print("delete key")
            if(leafNode.isUnderflow){

            }
        }
    }

    private fun handleUnderflow(node: LeafNode){
        val prevSibling: LeafNode? = leafNode.prev
        val nextSibling: LeafNode? = leafNode.next

        val (_, keyIdx) = traceNode.pop()
        val (parentNode, _) = traceNode.peek()
        when {
            prevSibling != null && prevSibling.hasSurplusKey -> print("redistribute left")
            nextSibling != null && nextSibling.hasSurplusKey -> print("redistribute right")
            prevSibling != null -> print("merge left")
            nextSibling != null -> print("merge right")
        }
    }

    private fun split(){
        while(traceNode.isNotEmpty()){
            val (currentNode, currentNodeIdx) = try {traceNode.pop()} catch (_: EmptyStackException) { throw IllegalStateException("Unexpected node trace data invalid")}
            if(currentNode.isOverflow){
                logger.info { "Node overflow split node $currentNodeIdx" }
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
                    logger.info { "split root node" }
                } else {
                    // parentNode 의 parentNodeIdx 정보는 나중에 parentNode split 할 때 필요함 -> peek 사용
                    parentNode = traceNode.peek().first as InternalNode
                    // parent key, value 삽입(leafNodeIdx 바로 오른쪽에)
                    parentNode.insert(currentNodeIdx, promotionKey, newNode)
                    logger.info { "split leaf node" }
                }
            }
        }
    }

    /**
     * 부모 노드의 키 K를 기준으로, 왼쪽 서브트리(subtree)의 모든 값은 K보다 작고, 오른쪽 서브트리의 모든 값은 K보다 크거나 같다. (>=).
     * find node by keyIndex, value.
     * p1: parentKey1 <= allChildNodeKeyOfPointer1 < parentKey2
     * p2: parentKey2 <= allChildNodeKeyOfPointer2 < parentKey3
     * key <= nodeKey 인 첫 nodeKey 를 찾고 그 왼쪽 자식으로 간다
     * 즉 searchKey 가 노드의 key 보다 작거나 같아야 한다.
     * 왼쪽으로 가야(더 작은범위부터 찾아야) 모든 범위를 찾을 수 있다.
     * 1, 5, 10 -> 3을 찾을 경우 5의 idx가 필요함
     * 1, 5, 10 -> 5를 찾을 경우 5의 idx가 필요함
     *
     * 경로를 보관할 stack이 필요
    * */
    fun search(key: ByteArray): Triple<LeafNode, Int, Boolean> {
        var node: Node = root ?: throw IllegalStateException("No root node")
        traceNode.push(node to -1)
        if(node.isLeaf){
            val (searchIdx, isExist) = node.search(key, comparator)
            return Triple(node as LeafNode, searchIdx, isExist)
        }
        var searchIdx: Int
        var isExist: Boolean
        do {
            val result = node.search(key, comparator)
            searchIdx = result.first
            node = node as InternalNode
            node = node.moveToChild(searchIdx)
            traceNode.push(node to searchIdx)
        } while(!node.isLeaf)
        val result = node.search(key, comparator)
        searchIdx = result.first
        isExist = result.second
        return Triple(node as LeafNode, searchIdx, isExist)
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

    private fun findLeftMostLeaf(): LeafNode?{
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

    /**
     * Test use only
     * */
    fun printTree(){

        data class QueueItem(
            val node: Node,
            val level: Int,
            val isLeaf: Boolean,
            val idx: Int
        )

        val start = root ?: return
        val queue = ArrayDeque<QueueItem>()
        queue.addLast(QueueItem(start, 0, start is LeafNode, 0))
        var prevLevel = 0
        while(queue.isNotEmpty()){
            val item = queue.removeFirst()
            var (node, level, isLeaf, idx) = item
            if(prevLevel != level){
                print("\n")
            }
            printNode(node, idx)
            if(!isLeaf){
                node = node as InternalNode
                for(idx in 0..(node.childCount-1)){
                    val childNode = node.moveToChild(idx)
                    queue.addLast(QueueItem(childNode, level + 1, childNode is LeafNode, idx))
                }
            }
            prevLevel = level
        }
        println("\n======================================")
    }

    fun printNode(node: Node, idx: Int){
        val keys = node.keyView
        val viewBuilder = StringBuilder()
        viewBuilder.append("[$idx] ")
        for(idx in keys.indices){
            val key: List<Any?> = KeyTool.unpack(keys[idx], schema)
            for(keyItem in key){
                viewBuilder.append("$keyItem|")
            }
            viewBuilder.append(" ")
        }
        viewBuilder.insert(0, "  ")
        print(viewBuilder.toString())
    }
}