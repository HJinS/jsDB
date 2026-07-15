package index.btree

import config.IndexConfig
import mu.KotlinLogging
import index.btree.node.InternalNode
import index.btree.node.LeafNode
import index.btree.node.Node
import index.exception.IndexException
import index.exception.NodeException
import index.exception.IllegalLatchStateException
import index.serializer.KeySerializer
import index.serializer.PageIDSerializer
import index.serializer.ValueSerializer
import index.util.BTreeOptMode
import storageEngine.util.LockMode
import storageEngine.page.PageLock

import storageEngine.StorageManager
import storageEngine.exception.SlottedPageException
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
import java.util.Arrays
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
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack<Triple<Long, Int, PageLock>>()
        val lockManager = LockManager(LockMode.WRITE)

        if (rootPageId != -1L) {
            val (leafNodePageId, _, _) = searchLeafNode(serializedKey, serializedValue, traceNode, lockManager, BTreeOptMode.INSERT)
            val writeLock = storageManager.fetchPage(leafNodePageId, lockManager.lockMode)
            lockManager.push(writeLock)
            writeLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                val node = Node.from(indexConfig, page, keySerializer)
                if(node.wouldOverflow(serializedKey, serializedValue)){
                    val separatorKey = page.getData(node.promotionKeyIdx() + 1).first
                    try{
                        val currentLockSize = lockManager.size
                        split(traceNode, lockManager)
                        if(Arrays.compareUnsigned(serializedKey, separatorKey) >= 0){
                            val rightLeafPageLock = lockManager.at(currentLockSize)
                            rightLeafPageLock.asWriteView { rightLeafBuffer ->
                                val rightPage = SlottedPage(indexConfig, rightLeafPageLock.pageId, rightLeafBuffer)
                                val rightNode = Node.from(indexConfig, rightPage, keySerializer) as LeafNode
                                rightNode.insert(serializedKey, serializedValue)
                            }
                        } else{
                            node.insert(serializedKey, serializedValue)
                        }
                    } catch (e: Exception) {
                        lockManager.close()
                        throw e
                    }
                } else{
                    node.insert(serializedKey, serializedValue)
                }
            }

            traceNode.clear()
        } else {
            val writeLock = storageManager.newPage(PageType.LEAF_NODE, lockManager.lockMode)
            rootPageId = writeLock.pageId
            lockManager.push(writeLock)

            writeLock.asWriteView { buffer ->
                val newPage = SlottedPage(indexConfig, rootPageId, buffer)
                val newNode = LeafNode(indexConfig, newPage, keySerializer)
                newNode.insertAt(0, serializedKey, serializedValue)
            }
        }
        lockManager.close()
        traceNode.clear()
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
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack<Triple<Long, Int, PageLock>>()
        val lockManager = LockManager(LockMode.WRITE)
        val (leafNodePageId, keyIdx, isExist) = searchLeafNode(serializedKey, null, traceNode, lockManager, BTreeOptMode.DELETE)
        if(isExist){
            var isUnderflow = false
            val leafLock = lockManager.last
            if(leafNodePageId != leafLock.pageId) throw IllegalLatchStateException.InvalidTraceObjectError(leafNodePageId)
            leafLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                val node = Node.from(indexConfig, page, keySerializer)
                node.deleteAt(keyIdx)
                if(node.isUnderflow && (leafNodePageId != rootPageId || node.keyCount == 0)){
                    isUnderflow = true
                }
            }
            if(isUnderflow){
                handleUnderflow(traceNode, lockManager)
            }
        }
        traceNode.clear()
        lockManager.close()
    }

    /**
     * Update value of certain key.
     *
     * If the key does not exist, do nothing.
     *
     * @param key key to find from B+tree of type [K].
     * @param newValue new value to update of type [V].
     */
    fun update(key: K, newValue: V) {
        val serializedKey = keySerializer.serialize(key)
        val serializedNewValue = valueSerializer.serialize(newValue)
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack()
        val lockManager = LockManager(LockMode.WRITE)

        val (leafNodePageId, keyIdx, isExist) = searchLeafNode(serializedKey, null, traceNode, lockManager, BTreeOptMode.UPDATE)

        if (isExist) {
            val leafLock = lockManager.last
            if (leafNodePageId != leafLock.pageId) throw IllegalLatchStateException.InvalidTraceObjectError(leafNodePageId)
            leafLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                page.updateData(keyIdx, serializedKey, serializedNewValue)
            }
        }

        traceNode.clear()
        lockManager.close()
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
    private fun handleUnderflow(traceNode: Stack<Triple<Long, Int, PageLock>>, lockManager: LockManager){
        var currentTrace = traceNode.pop()

        var currentPageId: Long = currentTrace.first
        var keyIdx: Int = currentTrace.second
        var currentLock: PageLock = currentTrace.third
        var isRoot: Boolean = traceNode.isEmpty()
        var isUnderflow = true

        while(!isRoot && isUnderflow) {
            val nextTrace = traceNode.peek()
            val parentLock = nextTrace.third
            var isDone = false
            
            currentLock.asWriteView { currentBuffer ->
                val currentPage = SlottedPage(indexConfig, currentPageId, currentBuffer)
                val currentNode = Node.from(indexConfig, currentPage, keySerializer)
                isUnderflow = currentNode.isUnderflow

                if(isUnderflow) {
                    parentLock.asWriteView { parentBuffer ->
                        val parentPage = SlottedPage(indexConfig, nextTrace.first, parentBuffer)
                        val parentNode = Node.from(indexConfig, parentPage, keySerializer) as InternalNode
                        val leftSiblingPageId = try{ parentNode.childPageId(keyIdx-1) } catch (_: SlottedPageException.SlotOutOfBoundException){ null }
                        val rightSiblingPageId = try{ parentNode.childPageId(keyIdx+1) } catch (_: SlottedPageException.SlotOutOfBoundException){ null }
                        val siblingPageIds = listOf(leftSiblingPageId, rightSiblingPageId)
                        val siblingLocks = mutableListOf<PageLock>()
                        for(siblingId in siblingPageIds){
                            if(siblingId != null && !isDone){
                                val siblingLock = storageManager.fetchPage(siblingId, lockManager.lockMode)
                                lockManager.push(siblingLock)
                                siblingLocks.add(siblingLock)
                                siblingLock.asWriteView { siblingBuffer ->
                                    val siblingPage = SlottedPage(indexConfig, siblingId, siblingBuffer)
                                    val siblingNode = Node.from(indexConfig, siblingPage, keySerializer)
                                    if(siblingNode.hasSurplusKey){
                                        currentNode.redistribute(siblingNode, parentNode, keyIdx)
                                        isDone = true
                                    }
                                }
                            }
                        }
                        var isMerged = false
                        if(!isDone){
                            /*
                            * merge 후에 right node 삭제 처리
                            * leaf node의 경우 sibling 재연결 처리 필요
                            * */
                            for(siblingLock in siblingLocks){
                                if(!isMerged){
                                    siblingLock.asWriteView { siblingBuffer ->
                                        val siblingPage = SlottedPage(indexConfig, siblingLock.pageId, siblingBuffer)
                                        val siblingNode = Node.from(indexConfig, siblingPage, keySerializer)
                                        val (_, rightPageId) = currentNode.merge(siblingNode, parentNode, keyIdx)
                                        isMerged = true
                                        val victimPageLock: PageLock = if(rightPageId == currentLock.pageId) currentLock else siblingLock
                                        lockManager.closeAndRemoveLock(victimPageLock)
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
            currentLock = currentTrace.third
            keyIdx = currentTrace.second
            isRoot = traceNode.isEmpty()
        }

        if(isRoot){
            var needChangeRoot = false
            var newRootId: Long? = null
            currentLock.asReadView { buffer ->
                val page = SlottedPage(indexConfig, currentPageId, buffer)
                val node = Node.from(indexConfig, page, keySerializer)
                when{
                    node is InternalNode && node.keyCount == 0 ->{
                        newRootId = node.childPageId(0)
                        needChangeRoot = true
                    }
                    node.isLeaf && node.keyCount == 0 -> {
                        newRootId = -1L
                        needChangeRoot = true
                    }
                }

            }
            if(needChangeRoot && newRootId != null){
                rootPageId = newRootId
                lockManager.closeAndRemoveLock(currentLock)
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
    private fun split(traceNode: Stack<Triple<Long, Int, PageLock>>, lockManager: LockManager){
        var continueLoop = true
        while(traceNode.isNotEmpty() && continueLoop){
            val (currentPageId, currentSlotIdx, currentPageLock) = try {traceNode.pop()} catch (e: EmptyStackException) { throw IndexException.InvalidTraceStackException(name, targetTable, e) }
            var newPageId: Long = -1L
            if(currentPageLock.pageId != currentPageId) throw IllegalLatchStateException.InvalidTraceObjectError(currentPageId)

            currentPageLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, currentPageId, buffer)
                val node = Node.from(indexConfig, page, keySerializer)

                // InternalNode: only split if it actually overflowed after receiving the promotion key
                if (node is InternalNode && !node.isOverflow) {
                    continueLoop = false
                    return@asWriteView
                }

                val nodeSplitData = when(node) {
                    is LeafNode -> {
                        val nodeSplitData = node.split()
                        val newLock = storageManager.newPage(PageType.LEAF_NODE, lockManager.lockMode)
                        lockManager.push(newLock)
                        newLock.asWriteView { newBuffer ->
                            newPageId = newLock.pageId
                            val newPage = SlottedPage(indexConfig, newPageId, newBuffer)
                            val newNode = Node.from(indexConfig, newPage, keySerializer) as LeafNode
                            newNode.appendAllData(nodeSplitData.splitKeys, nodeSplitData.splitValues)
                            node.linkNewSiblingNode(newNode)
                            nodeSplitData
                        }
                    }
                    is InternalNode -> {
                        val nodeSplitData = node.split()
                        val newLock = storageManager.newPage(PageType.INTERNAL_NODE, lockManager.lockMode)
                        lockManager.push(newLock)
                        newLock.asWriteView { newBuffer ->
                            newPageId = newLock.pageId
                            val newPage = SlottedPage(indexConfig, newPageId, newBuffer)
                            val newNode = Node.from(indexConfig, newPage, keySerializer) as InternalNode
                            newPage.leftMostChildPageId = nodeSplitData.leftMostChildPageId
                            newNode.appendAllData(nodeSplitData.splitKeys, nodeSplitData.splitValues)
                            nodeSplitData
                        }
                    }
                    else -> throw NodeException.InvalidNodeTypeException(node.page.type)
                }
                if(traceNode.isEmpty()){
                    val newLock = storageManager.newPage(PageType.INTERNAL_NODE, LockMode.WRITE)
                    lockManager.push(newLock)
                    newLock.asWriteView { newBuffer ->
                        val newRootPageId = newLock.pageId
                        val newRootPage = SlottedPage(indexConfig, newRootPageId, newBuffer)
                        val newRootNode = Node.from(indexConfig, newRootPage, keySerializer) as InternalNode
                        newRootPage.leftMostChildPageId = currentPageId
                        newRootNode.insert(nodeSplitData.promotionKey, pageIDSerializer.serialize(newPageId))
                        rootPageId = newRootPageId
                    }
                } else {
                    val parentPageId = traceNode.peek().first
                    val rootPageLock = traceNode.peek().third
                    rootPageLock.asWriteView { rootBuffer ->
                        val parentPage = SlottedPage(indexConfig, parentPageId, rootBuffer)
                        val parentNode = Node.from(indexConfig, parentPage, keySerializer) as InternalNode
                        parentNode.insertAt(currentSlotIdx, nodeSplitData.promotionKey, pageIDSerializer.serialize(newPageId))
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
    private fun searchLeafNode(
        key: ByteArray,
        value: ByteArray?,
        traceNode: Stack<Triple<Long, Int, PageLock>>,
        lockManager: LockManager,
        operationMode: BTreeOptMode
    ): Triple<Long, Int, Boolean> {
        if(rootPageId == -1L) throw IndexException.EmptyTreeException(name, targetTable)

        var pageIdCursor: Long = rootPageId
        val rootPageLock = storageManager.fetchPage(pageIdCursor, lockManager.lockMode)
        lockManager.push(rootPageLock)
        traceNode.push(Triple(rootPageId, -1, rootPageLock))
        while(true){
            val currentLock = lockManager.last
            var isSafeToUnlockAncestor = false
            currentLock.asReadView { buffer ->
                val currentPage = SlottedPage(indexConfig, pageIdCursor, buffer)
                val currentNode = Node.from(indexConfig, currentPage, keySerializer)
                if(currentNode.isSafeNode(operationMode, key, value)) isSafeToUnlockAncestor = true

                val result = currentNode.search(key)
                if(currentNode.isLeaf){
                    val (searchIdx, isExist) = currentNode.search(key, true)
                    return Triple(pageIdCursor, searchIdx, isExist)
                } else{
                    val currentInternalNode = currentNode as InternalNode
                    val nextPageId = currentInternalNode.childPageId(result.first)
                    val nextLock = storageManager.fetchPage(nextPageId, lockManager.lockMode)
                    if(isSafeToUnlockAncestor) lockManager.releaseAncestor(currentLock)
                    lockManager.push(nextLock)
                    traceNode.push(Triple(nextPageId, result.first, nextLock))
                    pageIdCursor = nextPageId
                }
            }
        }
    }

    fun search(key: K): V?{
        if (rootPageId == -1L) return null
        val serializedKey = keySerializer.serialize(key)
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack<Triple<Long, Int, PageLock>>()
        val lockManager = LockManager(LockMode.READ)
        val (leafNodePageId, keyIdx, isExist) = searchLeafNode(serializedKey, null, traceNode, lockManager, BTreeOptMode.SELECT)
        val lock = storageManager.fetchPage(leafNodePageId, lockManager.lockMode)
        lockManager.push(lock)
        val value: ByteArray? = lock.asReadView { buffer ->
            val currentPage = SlottedPage(indexConfig, leafNodePageId, buffer)
            val node = Node.from(indexConfig, currentPage, keySerializer)
            if(node.isSafeNode(BTreeOptMode.SELECT)) lockManager.releaseAncestor(lock)
            if (isExist) currentPage.getData(keyIdx).second else null
        }
        lockManager.close()
        traceNode.clear()
        return value?.let { valueSerializer.deserialize(it) }
    }

    /**
     * Traverse all the leaf nodes from left to right and return key, value of leaf node.
     *
     * @return Key, Value of the leaf node.
     * @see findLeftMostLeafPageId
     * */
    fun traverse(): List<Pair<K, V>>{
        val result = mutableListOf<Pair<K, V>>()
        val lockManager = LockManager(LockMode.READ)
        var leafNodePageIdCursor: Long? = findLeftMostLeafPageId(lockManager) ?: return emptyList()
        lockManager.push(storageManager.fetchPage(leafNodePageIdCursor!!, LockMode.READ))
        while(true){
            val currentLock = lockManager.last
            var isSafeToUnlockAncestor = false
            val nextLeafNodePageId = currentLock.asReadView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageIdCursor!!, buffer)
                val currentNode = Node.from(indexConfig, page, keySerializer)
                isSafeToUnlockAncestor = currentNode.isSafeNode(BTreeOptMode.SELECT)
                val keys = currentNode.keyView
                val values = currentNode.valueView
                for(idx in keys.indices){
                    val key: K = keySerializer.deserialize(keys[idx])
                    val value: V = valueSerializer.deserialize(values[idx])
                    result += key to value
                }
                page.rightSiblingPageId
            }
            if(isSafeToUnlockAncestor) lockManager.releaseAncestor(currentLock)
            currentLock.close()
            if(nextLeafNodePageId == -1L) break
            val nextLock = storageManager.fetchPage(nextLeafNodePageId, LockMode.READ)
            lockManager.push(nextLock)
            leafNodePageIdCursor = nextLeafNodePageId
        }
        lockManager.close()
        return result
    }

    /**
     * Find the left most leaf of the B+tree.
     *
     * @return The left most child of B+tree.
     * */
    private fun findLeftMostLeafPageId(lockManager: LockManager): Long?{
        var pageIdCursor = if(rootPageId != -1L) rootPageId else return null
        var isLeaf = false
        lockManager.push(storageManager.fetchPage(pageIdCursor, lockManager.lockMode))
        while(true){
            val currentPageLock = lockManager.last
            var isSafeToUnlockAncestor = false
            val nextPageId = currentPageLock.asReadView { buffer ->
                val currentPage = SlottedPage(indexConfig, pageIdCursor, buffer)
                val currentNode = Node.from(indexConfig, currentPage, keySerializer)
                isSafeToUnlockAncestor = currentNode.isSafeNode(BTreeOptMode.SELECT)
                if(currentNode.isLeaf){
                    isLeaf = true
                    pageIdCursor
                } else{
                    val currentInternalNode = currentNode as InternalNode
                    currentInternalNode.childPageId(0)
                }
            }
            val nextLock = storageManager.fetchPage(nextPageId, lockManager.lockMode)
            if(isSafeToUnlockAncestor) lockManager.releaseAncestor(currentPageLock)
            lockManager.push(nextLock)
            pageIdCursor = nextPageId
            if(isLeaf) break
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
            val idx: Int,
            val pageLock: PageLock
        )

        val startPageId = rootPageId
        val queue = ArrayDeque<QueueItem>()
        val lockManager = LockManager(LockMode.READ)
        val startLock = storageManager.fetchPage(startPageId, lockManager.lockMode)
        lockManager.push(startLock)
        val startNodeIsLeaf = startLock.asReadView { buffer ->
            val page = SlottedPage(indexConfig, startPageId, buffer)
            val node = Node.from(indexConfig, page, keySerializer)
            node.isLeaf
        }
        queue.addLast(QueueItem(startPageId, 0, startNodeIsLeaf, 0, startLock))
        var prevLevel = 0
        val viewBuilder = StringBuilder()
        while(queue.isNotEmpty()){
            val item = queue.removeFirst()
            val (currentPageId, level, isLeaf, idx, currentLock) = item
            if(prevLevel != level){
                viewBuilder.append("\n")
            }

            currentLock.asReadView { buffer ->
                val currentPage = SlottedPage(indexConfig, currentPageId, buffer)
                var currentNode = Node.from(indexConfig, currentPage, keySerializer)
                printNode(viewBuilder, currentNode, idx)
                if(!isLeaf){
                    currentNode = currentNode as InternalNode
                    for(i in 0..< currentNode.keyCount + 1){
                        val childNodePageId = currentNode.childPageId(i)
                        val childPageLock = storageManager.fetchPage(childNodePageId, lockManager.lockMode)
                        lockManager.push(childPageLock)
                        val childIsLeaf = childPageLock.asReadView { childBuffer ->
                            val childPage = SlottedPage(indexConfig, childNodePageId, childBuffer)
                            val childNode = Node.from(indexConfig, childPage, keySerializer)
                            childNode.isLeaf
                        }
                        queue.addLast(QueueItem(childNodePageId, level + 1, childIsLeaf, i, childPageLock))
                    }
                }
            }
            lockManager.closeAndRemoveLock(currentLock)
            prevLevel = level
        }
        lockManager.close()
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
