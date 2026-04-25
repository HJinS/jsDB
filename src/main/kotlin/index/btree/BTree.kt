package index.btree

import config.IndexConfig
import index.btree.node.InternalNode
import index.btree.node.LeafNode
import index.btree.node.Node
import index.dto.NodeSearchResult
import index.exception.BTreeException
import index.exception.IndexException
import index.exception.NodeException
import index.serializer.KeySerializer
import index.serializer.PageIDSerializer
import index.serializer.ValueSerializer
import mu.KotlinLogging
import storageEngine.StorageManager
import storageEngine.exception.SlottedPageException
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
import java.util.EmptyStackException
import java.util.Stack
import kotlin.collections.plusAssign
import kotlin.use

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
 * @property indexConfig Index configuration.
 * @constructor Create empty B tree.
 */
class BTree<K, V> (
    val name: String,
    val targetTable: String,
    private val storageManager: StorageManager,
    private val keySerializer: KeySerializer<K>,
    private val valueSerializer: ValueSerializer<V>,
    private val indexConfig: IndexConfig = IndexConfig
){
    private var rootPageId: Long = -1
    private val traceNode: Stack<Pair<Long, Int>> = Stack()

    companion object{
        private val pageIDSerializer: PageIDSerializer = PageIDSerializer()
    }

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
        if (rootPageId != -1L) {
            logger.info { "-------------------------------------------" }
            logger.info { "search key for insert - key: $key" }
            val (leafNodePageId, searchIdx, isExist) = searchLeafNode(serializedKey)
            logger.info { "search result - idx: $searchIdx, isExist: $isExist" }
            storageManager.fetchPage(leafNodePageId).use{ handle ->
                handle.asWriteView { buffer ->
                    val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                    val node = Node.from(indexConfig, page, keySerializer)
                    node.insert(serializedKey, serializedValue)
                    // check overflow and do split
                    if(node.isOverflow){
                        try{
                            split()
                        } catch (e: Exception) {
                            throw e
                        }
                    }
                }

            }
            traceNode.clear()
        } else {
            val newPageHandle = storageManager.newPage(PageType.LEAF_NODE)
            newPageHandle.use{ handle ->
                handle.asWriteView { buffer ->
                    rootPageId = handle.frame.pageId.get()
                    val newPage = SlottedPage(indexConfig, rootPageId, buffer)
                    val newNode = LeafNode(indexConfig, newPage, keySerializer)
                    newNode.insertAt(0, serializedKey, serializedValue)
                }
            }
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
     * @see Node.isUnderflow
     * */
    fun delete(key: K){
        val serializedKey = keySerializer.serialize(key)
        val (leafNodePageId, keyIdx, isExist) = searchLeafNode(serializedKey)
        if(isExist){
            printTree()
            var isUnderflow = false
            storageManager.fetchPage(leafNodePageId).use{ handle ->
                handle.asWriteView { buffer ->
                    val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                    val node = Node.from(indexConfig, page, keySerializer)
                    node.deleteAt(keyIdx)
                    if(node.isUnderflow){
                        isUnderflow = true
                    }
                }
            }
            if(isUnderflow){
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
        var currentPageId: Long = currentTrace.first
        var keyIdx: Int = currentTrace.second
        var isRoot: Boolean = traceNode.isEmpty()
        var isUnderflow = storageManager.fetchPage(currentPageId).use{ handle ->
            handle.asReadView { buffer ->
                val page = SlottedPage(indexConfig, currentPageId, buffer)
                val node = Node.from(indexConfig, page, keySerializer)
                node.isUnderflow
            }
        }

        while(!isRoot && isUnderflow) {
            val nextTrace = traceNode.peek()
            val parentPageId = nextTrace.first
            var isDone = false

            storageManager.fetchPage(currentPageId).use{ currentHandle ->
                currentHandle.asWriteView { currentBuffer ->
                    val currentPage = SlottedPage(indexConfig, currentPageId, currentBuffer)
                    val currentNode = Node.from(indexConfig, currentPage, keySerializer)
                    isUnderflow = currentNode.isUnderflow

                    storageManager.fetchPage(parentPageId).use{ parentHandle ->
                        parentHandle.asWriteView { parentBuffer ->
                            val parentPage = SlottedPage(indexConfig, currentPageId, parentBuffer)
                            val parentNode = Node.from(indexConfig, parentPage, keySerializer) as InternalNode
                            val leftSiblingPageId = try{ parentNode.childPageId(keyIdx-1) } catch (_: SlottedPageException.SlotOutOfBoundException){ null }
                            val rightSiblingPageId = try{ parentNode.childPageId(keyIdx+1) } catch (_: SlottedPageException.SlotOutOfBoundException){ null }
                            val siblingPageIds = listOf(leftSiblingPageId, rightSiblingPageId)
                            for(siblingId in siblingPageIds){
                                if(siblingId != null && !isDone){
                                    storageManager.fetchPage(siblingId).use{ siblingHandle ->
                                        siblingHandle.asWriteView { siblingBuffer ->
                                            val siblingPage = SlottedPage(indexConfig, siblingId, siblingBuffer)
                                            val siblingNode = Node.from(indexConfig, siblingPage, keySerializer)
                                            if(siblingNode.hasSurplusKey){
                                                currentNode.redistribute(siblingNode, parentNode, keyIdx)
                                                isDone = true
                                            }
                                        }
                                    }
                                }
                            }
                            var isMerged = false
                            var isLeafNode = false
                            if(!isDone){
                                /*
                                * merge 후에 right node 삭제 처리
                                * leaf node의 경우 sibling 재연결 처리 필요
                                * */
                                var mergeResult: Pair<Long, Long> = -1L to -1L
                                for(siblingId in siblingPageIds){
                                    if(siblingId != null && !isMerged){
                                        storageManager.fetchPage(siblingId).use{ siblingHandle ->
                                            siblingHandle.asWriteView { siblingBuffer ->
                                                val siblingPage = SlottedPage(indexConfig, siblingId, siblingBuffer)
                                                val siblingNode = Node.from(indexConfig, siblingPage, keySerializer)
                                                isLeafNode = currentNode.isLeaf
                                                if(siblingNode.hasSurplusKey){
                                                    mergeResult = currentNode.merge(siblingNode, parentNode, keyIdx)
                                                    isMerged = true
                                                }
                                            }
                                        }
                                    }
                                }
                                // merge후 추후 free page list 관리를 추가하여 page 삭제 로직 필요
                                // 추후 free frame 회수 및 flush 작업 필요
                                if(mergeResult != -1L to -1L){
                                    val (leftPageId, rightPageId) = mergeResult
                                    if(isMerged){
                                        storageManager.deletePage(rightPageId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if(isDone) break
            currentTrace = traceNode.pop()
            currentPageId = currentTrace.first
            keyIdx = currentTrace.second
            isRoot = traceNode.isEmpty()
        }

        if(isRoot){
            var needChangeRoot = false
            var newRootId: Long? = null
            storageManager.fetchPage(currentPageId).use{ handle ->
                handle.asReadView { buffer ->
                    val page = SlottedPage(indexConfig, currentPageId, buffer)
                    val node = Node.from(indexConfig, page, keySerializer)
                    if(node is InternalNode && node.keyCount == 0){
                        newRootId = node.childPageId(0)
                        needChangeRoot = true
                    }
                }
            }
            if(needChangeRoot && newRootId != null){
                rootPageId = newRootId!!
                storageManager.deletePage(currentPageId)
                // 트리의 메타데이터(rootPageId) 를 디스크에 써주는 내용 추가해야함

            }
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
            val (currentPageId, currentSlotIdx) = try {traceNode.pop()} catch (e: EmptyStackException) { throw IndexException.InvalidTraceStackException(name, targetTable, e) }
            var newPageId: Long = -1L
            storageManager.fetchPage(currentPageId).use { handle ->
                handle.asWriteView { buffer ->
                    val page = SlottedPage(indexConfig, currentPageId, buffer)
                    val node = Node.from(indexConfig, page, keySerializer)
                    if(node.isOverflow){
                        val nodeSplitData = when(node) {
                            is LeafNode -> {
                                val nodeSplitData = node.split()
                                storageManager.newPage(PageType.LEAF_NODE).use { handle ->
                                    handle.asWriteView { buffer ->
                                        newPageId = handle.frame.pageId.get()
                                        val newPage = SlottedPage(indexConfig, newPageId, buffer)
                                        val newNode = Node.from(indexConfig, newPage, keySerializer) as LeafNode
                                        newNode.appendAllData(nodeSplitData.splitKeys, nodeSplitData.splitValues)
                                        node.linkNewSiblingNode(newNode)
                                        nodeSplitData
                                    }
                                }
                            }
                            is InternalNode -> {
                                val nodeSplitData = node.split()
                                storageManager.newPage(PageType.INTERNAL_NODE).use { handle ->
                                    handle.asWriteView { buffer ->
                                        newPageId = handle.frame.pageId.get()
                                        val newPage = SlottedPage(indexConfig, newPageId, buffer)
                                        val newNode = Node.from(indexConfig, newPage, keySerializer) as InternalNode
                                        newPage.leftMostChildPageId = nodeSplitData.leftMostChildPageId
                                        newNode.appendAllData(nodeSplitData.splitKeys, nodeSplitData.splitValues)
                                        nodeSplitData
                                    }
                                }
                            }
                            else -> throw NodeException.InvalidNodeTypeException(node.page.type)
                        }
                        if(traceNode.isEmpty()){
                            storageManager.newPage(PageType.INTERNAL_NODE).use { handle ->
                                handle.asWriteView { buffer ->
                                    val newRootPageId = handle.frame.pageId.get()
                                    val newRootPage = SlottedPage(indexConfig, newRootPageId, buffer)
                                    val newRootNode = Node.from(indexConfig, newRootPage, keySerializer) as InternalNode
                                    newRootPage.leftMostChildPageId = currentPageId
                                    newRootNode.insert(nodeSplitData.promotionKey, pageIDSerializer.serialize(newPageId))
                                    rootPageId = newRootPageId
                                }
                            }
                        } else {
                            val parentPageId = traceNode.peek().first
                            storageManager.fetchPage(parentPageId).use { handle ->
                                handle.asWriteView { buffer ->
                                    val parentPage = SlottedPage(indexConfig, parentPageId, buffer)
                                    val parentNode = Node.from(indexConfig, parentPage, keySerializer) as InternalNode
                                    parentNode.insertAt(currentSlotIdx, nodeSplitData.promotionKey, pageIDSerializer.serialize(newPageId))
                                }
                            }
                        }
                    }
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
     * @see Node.search
     * @see Node.search
     **/
    private fun searchLeafNode(key: ByteArray): Triple<Long, Int, Boolean> {
        if(rootPageId == -1L) throw IndexException.EmptyTreeException(name, targetTable)
        traceNode.push(rootPageId to -1)
        val rootNodeHandle = storageManager.fetchPage(rootPageId)

        rootNodeHandle.use{ handle ->
            handle.asReadView { buffer ->
                val rootPage = SlottedPage(indexConfig, rootPageId, buffer)
                val node = Node.from(indexConfig, rootPage, keySerializer)
                if(node is InternalNode && node.isLeaf){
                    val (searchIdx, isExist) = node.search(key)
                    return Triple(node.page.pageId, searchIdx, isExist)
                }
            }
        }

        var pageIdCursor: Long = rootPageId
        while(true){
            val nodeSearchResult: NodeSearchResult = storageManager.fetchPage(pageIdCursor).use { handle ->
                handle.asReadView { buffer ->
                    val currentPage = SlottedPage(indexConfig, pageIdCursor, buffer)
                    val currentNode = Node.from(indexConfig, currentPage, keySerializer)
                    val result = currentNode.search(key)
                    if(currentNode.isLeaf){
                        NodeSearchResult(pageIdCursor, result.first, result.second, true)
                    } else{
                        val currentInternalNode = currentNode as InternalNode
                        val nextPageId = currentInternalNode.childPageId(result.first)
                        NodeSearchResult(nextPageId, result.first, result.second, false)
                    }
                }
            }
            when(nodeSearchResult.isLeaf){
                true -> break
                false -> {
                    traceNode.push(nodeSearchResult.pageId to nodeSearchResult.searchIdx)
                }
            }

            pageIdCursor = nodeSearchResult.pageId
        }
        val nodeSearchResult: NodeSearchResult = storageManager.fetchPage(pageIdCursor).use { handle ->
            handle.asReadView { buffer ->
                val page = SlottedPage(indexConfig, pageIdCursor, buffer)
                val node = Node.from(indexConfig, page, keySerializer) as LeafNode
                val result = node.search(key)
                NodeSearchResult(pageIdCursor, result.first, result.second, true)
            }

        }
        return Triple(pageIdCursor, nodeSearchResult.searchIdx, nodeSearchResult.isExist)
    }

    fun search(key: K): V?{
        val serializedKey = keySerializer.serialize(key)
        val (leafNodePageId, keyIdx, isExist) = searchLeafNode(serializedKey)
        val value: ByteArray = storageManager.fetchPage(leafNodePageId).use { handle ->
            handle.asReadView { buffer ->
                val currentPage = SlottedPage(indexConfig, leafNodePageId, buffer)
                currentPage.getData(keyIdx).second
            }
        }
        return if(isExist) valueSerializer.deserialize(value) else null
    }

    /**
     * Traverse all the leaf nodes from left to right and return key, value of leaf node.
     *
     * @return Key, Value of the leaf node.
     * @see findLeftMostLeafPageId
     * */
    fun traverse(): List<Pair<K, V>>{
        val result = mutableListOf<Pair<K, V>>()
        var leafNodePageIdCursor: Long? = findLeftMostLeafPageId() ?: throw BTreeException.LeafNodeNotFoundException(null)
        while(leafNodePageIdCursor != null){
            val nextLeafNodePageId = storageManager.fetchPage(leafNodePageIdCursor).use{ handle ->
                handle.asReadView { buffer ->
                    val page = SlottedPage(indexConfig, leafNodePageIdCursor!!, buffer)
                    val currentNode = Node.from(indexConfig, page, keySerializer)
                    val keys = currentNode.keyView
                    val values = currentNode.valueView
                    for(idx in keys.indices){
                        val key: K = keySerializer.deserialize(keys[idx])
                        val value: V = valueSerializer.deserialize(values[idx])
                        result += key to value
                    }
                    page.rightSiblingPageId
                }
            }
            leafNodePageIdCursor = nextLeafNodePageId
        }
        return result
    }

    /**
     * Find the left most leaf of the B+tree.
     *
     * @return The left most child of B+tree.
     * */
    private fun findLeftMostLeafPageId(): Long?{
        var pageIdCursor = rootPageId ?: return null
        var leaf = false
        while(true){
            val nextPageId = storageManager.fetchPage(pageIdCursor).use { handle ->
                handle.asReadView { buffer ->
                    val currentPage = SlottedPage(indexConfig, pageIdCursor, buffer)
                    val currentNode = Node.from(indexConfig, currentPage, keySerializer)
                    if(currentNode.isLeaf){
                        leaf = true
                        pageIdCursor
                    } else{
                        val currentInternalNode = currentNode as InternalNode
                        currentInternalNode.childPageId(0)
                    }
                }
            }
            pageIdCursor = nextPageId
            if(leaf) break
        }
        return pageIdCursor
    }

    /**
     * Print the tree with logger. Only for test.
     * */
    fun printTree(){

        data class QueueItem(
            val pageId: Long,
            val level: Int,
            val isLeaf: Boolean,
            val idx: Int
        )

        val startPageId = rootPageId ?: return
        val queue = ArrayDeque<QueueItem>()
        val isLeaf = storageManager.fetchPage(startPageId).use{ handle ->
            handle.asReadView { buffer ->
                val page = SlottedPage(indexConfig, startPageId, buffer)
                val node = Node.from(indexConfig, page, keySerializer)
                node.isLeaf
            }
        }
        queue.addLast(QueueItem(startPageId, 0, isLeaf, 0))
        var prevLevel = 0
        val viewBuilder = StringBuilder()
        while(queue.isNotEmpty()){
            val item = queue.removeFirst()
            var (currentPageId, level, isLeaf, idx) = item
            if(prevLevel != level){
                viewBuilder.append("\n")
            }
            storageManager.fetchPage(currentPageId).use { handle ->
                handle.asReadView { buffer ->
                    val currentPage = SlottedPage(indexConfig, currentPageId, buffer)
                    var currentNode = Node.from(indexConfig, currentPage, keySerializer)
                    printNode(viewBuilder, currentNode, idx)
                    if(!isLeaf){
                        currentNode = currentNode as InternalNode
                        for(i in 0..< currentNode.keyCount + 1){
                            val childNodePageId = currentNode.childPageId(i)

                            val isLeaf = storageManager.fetchPage(childNodePageId).use{ childHandle ->
                                childHandle.asReadView { childBuffer ->
                                    val childPage = SlottedPage(indexConfig, childNodePageId, childBuffer)
                                    val childNode = Node.from(indexConfig, childPage, keySerializer)
                                    childNode.isLeaf
                                }
                            }
                            queue.addLast(QueueItem(childNodePageId, level + 1, isLeaf, i))
                        }
                    }
                }
            }
            prevLevel = level
        }
        logger.info { "\n\n$viewBuilder\n\n"}
    }

    /**
     * Print a single node.
     * */
    private fun printNode(viewBuilder: StringBuilder, node: Node<K>, idx: Int){
        val keys = node.keyView
        viewBuilder.append("   [${node.hashCode()}][$idx] ")

        for(i in keys.indices){
            val key: K = keySerializer.deserialize(keys[i])
            viewBuilder.append(keySerializer.format(key))
            viewBuilder.append(" ")
        }
    }
}
