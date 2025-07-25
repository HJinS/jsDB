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
        root?.let {
            logger.info { "-------------------------------------------" }
            logger.info { "search key for insert - key: $key" }
            val (leafNode, idx, result) = search(packedKey)
            logger.info { "search result - idx: $idx, isExist: $result" }
            leafNode.insert(idx, packedKey, key)
            if(leafNode.isOverflow){
                try{
                    split()
                } catch (e: Exception) {
                    throw e
                }
            }
            traceNode.clear()
            // check overflow and do split
        } ?: run {
            root = LeafNode(mutableListOf(packedKey), maxKeys, mutableListOf(key))
        }
        logger.info { "-------------------------------------------" }
        printTree()
        logger.info { "-------------------------------------------" }
    }


    /**
     * delete
     * key의 최소 개수는 ceil(maxKeys/2) 이상 이어야함.
     *
     * 1. 삭제할 key 를 찾아 leafNode 로 내려감
     * 2. key <= nodeKey 기준으로 검색(기준은 기존과 동일)
     * 3. handle underflow
     * */
    fun delete(key: List<Any>){
        val packedKey: ByteArray = KeyTool.pack(key, schema)
        val (leafNode, keyIdx, isExist) = search(packedKey)
        if(isExist){
            leafNode.delete(keyIdx)
            print("delete key")
            if(leafNode.isUnderflow){
                handleUnderflow()
            }
        }
    }

    /**
     * Handle underflow by following steps.
     *   1. Redistribution @see [LeafNode.redistribute] @see [InternalNode.redistribute]
     *       The minimum key of right node became new separate key.
     *       - Borrow from the left node.
     *         1. Take the maximum key from the left node and insert to myself as the minimum key.
     *         2. Update the separate key to the new key from step 1.
     *       - Borrow from the right node.
     *         1. Take the minimum key from the right node and insert to myself as the maximum key.
     *         2. Update the separate key with a new minimum key of the right node.
     *   2. Merge @see [LeafNode.merge] @see [InternalNode.merge]
     *       The right node will be merged into left node.
     *       3. Should rebalance continuously to the root node. 상위 노드까지 확인해서 게속적으로 rebalancing 필요.
     * */
    private fun handleUnderflow(){
        var currentTrace = traceNode.pop()
        var currentNode: Node = currentTrace.first
        var keyIdx: Int = currentTrace.second
        var isRoot: Boolean = traceNode.isEmpty()

        while(!isRoot && currentNode.isUnderflow) {
            val nextTrace = traceNode.peek()
            val parentNode: InternalNode = nextTrace.first as InternalNode
            val prevSibling = try { parentNode.moveToChild(keyIdx - 1) } catch (_: IndexOutOfBoundsException) { null }
            val nextSibling = try { parentNode.moveToChild(keyIdx + 1) } catch (_: IndexOutOfBoundsException) { null }

            printTree()
            when {
                prevSibling != null && prevSibling.hasSurplusKey -> {
                    currentNode.redistribute(prevSibling, parentNode, keyIdx, schema)
                    break
                }
                nextSibling != null && nextSibling.hasSurplusKey -> {
                    currentNode.redistribute(nextSibling, parentNode, keyIdx, schema)
                    break
                }
                prevSibling != null -> currentNode.merge(prevSibling, parentNode, keyIdx, schema)
                nextSibling != null -> currentNode.merge(nextSibling, parentNode, keyIdx, schema)
            }

            currentTrace = traceNode.pop()
            currentNode = currentTrace.first
            isRoot = traceNode.isEmpty()
            keyIdx = currentTrace.second
        }

        if(isRoot && currentNode.keys.isEmpty()){
            root = if(currentNode.isLeaf) currentNode as LeafNode else currentNode as InternalNode
        }
    }

    private fun split(){
        while(traceNode.isNotEmpty()){
            val (currentNode, currentNodeIdx) = try {traceNode.pop()} catch (_: EmptyStackException) { throw IllegalStateException("Unexpected node trace data invalid")}
            if(currentNode.isOverflow){
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
                    parentNode.insert(currentNodeIdx, promotionKey, newNode)
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
        while(!node.isLeaf){
            val result = node.search(key, comparator)
            searchIdx = result.first
            node = node as InternalNode
            node = node.moveToChild(searchIdx)
            traceNode.push(node to searchIdx)
            logger.info { "move to [${(node.hashCode())}][$searchIdx] result: ${result.second}" }
        }
        logger.info { "End of loop" }
        val result = node.search(key, comparator, true)
        searchIdx = result.first
        isExist = result.second
        logger.info { "final result [${(node.hashCode())}][$searchIdx] result: ${result.second}" }
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
        val viewBuilder = StringBuilder()
        while(queue.isNotEmpty()){
            val item = queue.removeFirst()
            var (node, level, isLeaf, idx) = item
            if(prevLevel != level){
                viewBuilder.append("\n")
            }
            printNode(viewBuilder, node, idx)
            if(!isLeaf){
                node = node as InternalNode
                for(idx in 0..(node.childCount-1)){
                    val childNode = node.moveToChild(idx)
                    queue.addLast(QueueItem(childNode, level + 1, childNode is LeafNode, idx))
                }
            }
            prevLevel = level
        }
        logger.info { "\n\n$viewBuilder\n\n"}
    }

    fun printNode(viewBuilder: StringBuilder, node: Node, idx: Int){
        val keys = node.keyView
        viewBuilder.append("   [${node.hashCode()}][$idx] ")
        for(idx in keys.indices){
            val key: List<Any?> = KeyTool.unpack(keys[idx], schema)
            for(keyItem in key){
                viewBuilder.append("$keyItem|")
            }
            viewBuilder.append(" ")
        }
    }
}