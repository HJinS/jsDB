package index.btree

import index.comparator.KeyComparator
import index.serializer.KeySerializer
import index.serializer.ValueSerializer
import index.util.MAX_KEYS
import mu.KotlinLogging
import java.util.EmptyStackException
import java.util.Stack
import kotlin.collections.plusAssign

val logger = KotlinLogging.logger {}


/**
 * B+tree implementation.
 *
 * @param K Type of key.
 * @param V Type of value.
 * @property name Name of BTree.
 * @property targetTable Table to apply.
 * @property keySerializer Serializer to serialize keys to ByteArray, comparable format.
 * @property valueSerializer Serializer to serialize values to ByteArray. IT's different form serializing keys.
 * @property keyComparator Comparator for comparing keys.
 * @property maxKeys MaxKeys in one node except root.
 * @property allowDuplicate Flag to allow duplicate or not.
 * @constructor Create empty B tree.
 */
class BTree<K, V> (
    val name: String,
    val targetTable: String,
    private val keySerializer: KeySerializer<K>,
    private val valueSerializer: ValueSerializer<V>,
    private val keyComparator: KeyComparator,
    private val maxKeys: Int = MAX_KEYS,
    private val allowDuplicate: Boolean = true
){
    private var root: Node? = null
    private val comparator = Comparator<ByteArray> {
        a, b -> keyComparator.compare(a, b)
    }
    private val traceNode: Stack<Pair<Node, Int>> = Stack()

    /**
     * Insert the provided key and value to B+tree.
     * - Find the place to insert.
     * - Insert the key and value.
     * - Split the node at overflow.
     *
     * @param key key to insert into B+tree of type [K].
     * @param value value to insert into B+tree of type [V].
     * @see split
     */
    fun insert(key: K, value: V) {
        val serializedValue = valueSerializer.serialize(value)
        val serializedKey = keySerializer.serialize(key)
        root?.let {
            logger.info { "-------------------------------------------" }
            logger.info { "search key for insert - key: $key" }
            val (leafNode, idx, result) = searchLeafNode(serializedKey)
            logger.info { "search result - idx: $idx, isExist: $result" }
            leafNode.insert(idx, serializedKey, serializedValue)
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
            root = LeafNode(mutableListOf(serializedKey), maxKeys, mutableListOf(serializedValue))
        }
        logger.info { "-------------------------------------------" }
        printTree()
        logger.info { "-------------------------------------------" }
    }


    /**
     * Delete certain key.
     *
     * If the key not exist, do nothing.
     *
     * If underflow, re-balance tree by [handleUnderflow]
     * - Underflow condition: keySize < maxKeys / 2
     *
     * @param key key to delete from B+tree of type [K].
     * @see handleUnderflow
     * @see LeafNode.isUnderflow
     * */
    fun delete(key: K){
        val serializedKey = keySerializer.serialize(key)
        val (leafNode, keyIdx, isExist) = searchLeafNode(serializedKey)
        if(isExist){
            printTree()
            leafNode.delete(keyIdx)
            if(leafNode.isUnderflow){
                handleUnderflow()
            }
        }
        traceNode.clear()
    }

    /**
     * Handle underflow by following steps.
     *
     * Redistribution
     *
     * @see LeafNode.redistribute
     * @see InternalNode.redistribute
     *  - The minimum key of the right node became a new separate key.
     *
     * Merge
     *
     * @see LeafNode.merge
     * @see InternalNode.merge
     *  - The right node will be merged into the left node.
     *
     * Should rebalance continuously to the root node.
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
                    currentNode.redistribute(prevSibling, parentNode, keyIdx)
                    break
                }
                nextSibling != null && nextSibling.hasSurplusKey -> {
                    currentNode.redistribute(nextSibling, parentNode, keyIdx)
                    break
                }
                prevSibling != null -> currentNode.merge(prevSibling, parentNode, keyIdx)
                nextSibling != null -> currentNode.merge(nextSibling, parentNode, keyIdx)
            }

            currentTrace = traceNode.pop()
            currentNode = currentTrace.first
            isRoot = traceNode.isEmpty()
            keyIdx = currentTrace.second
        }

        if(isRoot && currentNode.keys.isEmpty()){
            val currentNodeInternal = currentNode as InternalNode
            val nextChildNode = currentNodeInternal.moveToChild(0)
            root = if(nextChildNode.isLeaf) nextChildNode as LeafNode else nextChildNode as InternalNode
        }
    }


    /**
     * Split the node so that the B+tree remains balanced.
     *
     * - Split the node using search history.
     * - Rearrange a linked list at the leaf node.
     * - Root node split means additional height to the B+tree.
     * - Clear the search history after the insert.
     *
     * @see LeafNode.split
     * @see InternalNode.split
     * */
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
     * Find leaf node using provided [key].
     *
     *       Key1   Key2   Key3
     *    P1     P2     P3     P4
     *
     * - ParentKey < All keys from the left subtree.
     * - ParentKey <= All keys from the right subtree.
     * - Find the key which is greater than provided key within key 1-3 and go down to left subtree of that key.
     * - Save the search path for future use.
     * - P1 < Key1
     * - Key1 <= P2 < Key2
     * - Key2 <= P3 < Key3
     * - Key3 <= P4
     *
     * Example
     *
     *        1      5     10
     *    P1     P2     P3     P4
     *
     * - Searching for 3, go P2.
     * - Searching for 5, go P3.
     *
     * @param key Key to find leaf node
     * @see LeafNode.search
     * @see InternalNode.search
     **/
    private fun searchLeafNode(key: ByteArray): Triple<LeafNode, Int, Boolean> {
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

    fun search(key: K): V?{
        val serializedKey = keySerializer.serialize(key)
        val (leafNode, keyIdx, isExist) = searchLeafNode(serializedKey)
        return if(isExist) valueSerializer.deserialize(leafNode.values[keyIdx]) else null
    }

    /**
     * Traverse all the leaf nodes from left to right and return key, value of leaf node.
     *
     * @return Key, Value of the leaf node.
     * @see findLeftMostLeaf
     * */
    fun traverse(): List<Pair<K, V>>{
        val result = mutableListOf<Pair<K, V>>()
        var currentNode: Node? = findLeftMostLeaf() ?: throw java.lang.IllegalStateException("Empty tree")
        while(currentNode != null){
            currentNode = currentNode as LeafNode
            val keys = currentNode.keyView
            for(i in keys.indices){
                val key: K = keySerializer.deserialize(keys[i])
                val value: V = valueSerializer.deserialize(currentNode.values[i])
                result += key to value
            }
            currentNode = currentNode.next
        }
        return result
    }

    /**
     * Find the left most leaf of the B+tree.
     *
     * @return The left most child of B+tree.
     * */
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
     * Print the tree with logger. Only for test.
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
                for(i in 0..<node.childCount){
                    val childNode = node.moveToChild(i)
                    queue.addLast(QueueItem(childNode, level + 1, childNode is LeafNode, i))
                }
            }
            prevLevel = level
        }
        logger.info { "\n\n$viewBuilder\n\n"}
    }

    /**
     * Print a single node.
     * */
    private fun printNode(viewBuilder: StringBuilder, node: Node, idx: Int){
        val keys = node.keyView
        viewBuilder.append("   [${node.hashCode()}][$idx] ")

        for(i in keys.indices){
            val key: K = keySerializer.deserialize(keys[i])
            viewBuilder.append(keySerializer.format(key))
            viewBuilder.append(" ")
        }
    }
}