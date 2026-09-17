package index.btree

import config.IndexConfig
import exception.IndexException
import exception.StorageEngineException
import index.btree.node.InternalNode
import index.btree.node.LeafNode
import index.btree.node.Node
import index.data.SearchPosition
import index.serializer.PageIDSerializer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Arrays
import java.util.EmptyStackException
import java.util.Stack
import kotlin.collections.ArrayDeque
import storageEngine.StorageManager
import storageEngine.page.PageLock
import storageEngine.page.SlottedPage
import util.EngineErrorDetail
import util.INVALID_PAGE_ID
import util.LockMode
import util.PageType

val logger = KotlinLogging.logger {}

/**
 * B+tree implementation.
 *
 * @param K Type of key.
 * @param V Type of value.
 * @property name Name of BTree.
 * @property targetTable Table to apply.
 * @property keySerializer Serializer to serialize keys to ByteArray, comparable format.
 * @property valueSerializer Serializer to serialize values to ByteArray. IT's different form
 *   serializing keys.
 * @property indexConfig Index configuration.
 * @constructor Create empty B tree.
 */
class BTree(
    val name: String,
    val targetTable: String,
    private val storageManager: StorageManager,
    private val indexConfig: IndexConfig,
    private var rootPageId: Long,
    private val onRootChanged: ((Long) -> Unit)? = null,
) {
    companion object {
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
    fun insert(
        key: ByteArray,
        value: ByteArray,
    ) {
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack<Triple<Long, Int, PageLock>>()
        val lockManager = LockManager(LockMode.WRITE)

        if (rootPageId != INVALID_PAGE_ID) {
            val (leafNodePageId, _, _) =
                searchLeafNode(
                    key,
                    value,
                    traceNode,
                    lockManager,
                    BTreeOptMode.INSERT,
                )
            val writeLock = storageManager.fetchPage(leafNodePageId, lockManager.lockMode)
            lockManager.push(writeLock)
            writeLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                val node = Node.from(indexConfig, page)
                checkOverflowAndSplitFirst(node, page, key, value, lockManager, traceNode)
            }
            traceNode.clear()
        } else {
            val writeLock = storageManager.newPage(PageType.LEAF_NODE, lockManager.lockMode)
            changeRootPageId(writeLock.pageId)
            lockManager.push(writeLock)

            writeLock.asWriteView { buffer ->
                val newPage = SlottedPage(indexConfig, rootPageId, buffer)
                val newNode = LeafNode(indexConfig, newPage)
                newNode.insertAt(0, key, value)
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
     */
    fun delete(key: ByteArray) {
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack<Triple<Long, Int, PageLock>>()
        val lockManager = LockManager(LockMode.WRITE)
        val (leafNodePageId, keyIdx, isExist) =
            searchLeafNode(
                key,
                null,
                traceNode,
                lockManager,
                BTreeOptMode.DELETE,
            )
        if (isExist) {
            var isUnderflow = false
            val leafLock = lockManager.last
            if (leafNodePageId != leafLock.pageId) {
                throw IndexException.InvalidTraceObject(
                    EngineErrorDetail(
                        pageId = leafNodePageId,
                        reason =
                            "The provided trace object does not match the most recently pushed lock in the latch queue.",
                    )
                )
            }
            leafLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                val node = Node.from(indexConfig, page)
                node.deleteAt(keyIdx)
                isUnderflow = checkUnderflow(node, leafNodePageId)
                // When the first key of a leaf is deleted, the ancestor separator that points
                // to this subtree becomes stale. Propagate the new first key upward.
                // (Underflow case: handleUnderflow may overwrite this with the correct value.)
                if (keyIdx == 0 && node.keyCount > 0) {
                    val newFirstKey = page.getData(0).first
                    propagateSeparatorUpdate(traceNode, newFirstKey, lockManager)
                }
            }
            if (isUnderflow) {
                handleUnderflow(traceNode, lockManager)
            }
        }
        traceNode.clear()
        lockManager.close()
    }

    /**
     * Update key and value of certain key.
     *
     * If the key does not exist, do nothing.
     *
     * @param key key to find from B+tree of type [K].
     * @param newValue new value to update of type [V].
     */
    fun update(
        key: ByteArray,
        newKey: ByteArray,
        newValue: ByteArray,
    ) {
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack()
        val lockManager = LockManager(LockMode.WRITE)
        val (leafNodePageId, keyIdx, isExist) =
            searchLeafNode(
                key,
                null,
                traceNode,
                lockManager,
                BTreeOptMode.UPDATE,
            )
        val isSameKey = key.contentEquals(newKey)
        var searchAnotherPage = false
        var isUnderflow = false
        if (isExist) {
            val leafLock = lockManager.last
            if (leafNodePageId != leafLock.pageId) {
                throw IndexException.InvalidTraceObject(
                    EngineErrorDetail(
                        pageId = leafNodePageId,
                        reason =
                            "The provided trace object does not match the most recently pushed lock in the latch queue.",
                    )
                )
            }
            leafLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageId, buffer)
                val node = Node.from(indexConfig, page)
                page.deleteData(keyIdx)
                if (isSameKey) {
                    checkOverflowAndSplitFirst(node, page, newKey, newValue, lockManager, traceNode)
                } else {
                    if (keyIdx == 0 && node.keyCount > 0) {
                        val newFirstKey = page.getData(0).first
                        propagateSeparatorUpdate(traceNode, newFirstKey, lockManager)
                    }
                    val insertSlot = node.search(newKey).first
                    if (0 < insertSlot && insertSlot < node.keyCount - 1) {
                        checkOverflowAndSplitFirst(
                            node,
                            page,
                            newKey,
                            newValue,
                            lockManager,
                            traceNode,
                        )
                    } else {
                        isUnderflow = checkUnderflow(node, leafNodePageId)
                        searchAnotherPage = true
                    }
                }
            }
            if (searchAnotherPage) {
                if (isUnderflow) handleUnderflow(traceNode, lockManager)
                traceNode.clear()
                lockManager.close()
                insert(newKey, newValue)
                return
            }
        }
        traceNode.clear()
        lockManager.close()
    }

    fun search(key: ByteArray): ByteArray? {
        if (rootPageId == INVALID_PAGE_ID) return null
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack<Triple<Long, Int, PageLock>>()
        val lockManager = LockManager(LockMode.READ)
        val (leafNodePageId, keyIdx, isExist) =
            searchLeafNode(key, null, traceNode, lockManager, BTreeOptMode.SELECT)
        val lock = storageManager.fetchPage(leafNodePageId, lockManager.lockMode)
        lockManager.push(lock)
        val value: ByteArray? = lock.asReadView { buffer ->
            val currentPage = SlottedPage(indexConfig, leafNodePageId, buffer)
            val node = Node.from(indexConfig, currentPage)
            if (node.isSafeNode(BTreeOptMode.SELECT)) lockManager.releaseAncestor(lock)
            if (isExist) currentPage.getData(keyIdx).second else null
        }
        lockManager.close()
        traceNode.clear()
        return value
    }

    fun search(
        key: ByteArray,
        direction: ScanDirection,
        boundGiven: Boolean,
    ): Cursor? {
        if (rootPageId == INVALID_PAGE_ID) return null
        val traceNode: Stack<Triple<Long, Int, PageLock>> = Stack<Triple<Long, Int, PageLock>>()
        val lockManager = LockManager(LockMode.READ)
        val searchPosition =
            findSearchPosition(key, boundGiven, direction, lockManager, traceNode) ?: return null
        return Cursor(lockManager, searchPosition, direction, storageManager, indexConfig)
    }

    /**
     * Computes the [SearchPosition] that the first [Cursor.step] will read, seeking off of
     * whichever bound this scan direction starts from (the lower bound for FORWARD, the upper
     * bound for BACKWARD).
     *
     * [key] is boundary bytes the caller (Table) has already built after resolving open vs.
     * closed on its own - inclusive calls `serialize`, exclusive calls `serializeUpper` - so this
     * function never knows which one produced it. There are three cases:
     *
     * - [boundGiven] is false: there's no condition at all on this direction's seek side (e.g. no
     *   WHERE clause on that column). With nothing to compare against, descend straight to the
     *   leftmost/rightmost leaf via [findExtremeLeafPageId].
     * - [key] is null: a condition existed, but `serializeUpper` couldn't produce a successor —
     *   the value's last column is DESC and exactly NULL, so that group already sits at the
     *   tree's physical right edge. For BACKWARD, that edge group *is* the seek target, so land
     *   on the rightmost leaf. For FORWARD, we were asked for something greater than the very
     *   end of the tree, which can't exist, so there's no result at all (null).
     * - Otherwise: [searchLeafNode] finds the first slot with key >= the given bytes (a plain
     *   lower-bound search). Because each direction's boundary bytes are already built to point
     *   at the right spot, FORWARD's result is the answer as-is, while BACKWARD's result points
     *   one slot past the last item we actually want - hence the `- 1`.
     */
    private fun findSearchPosition(
        key: ByteArray?,
        boundGiven: Boolean,
        direction: ScanDirection,
        lockManager: LockManager,
        traceNode: Stack<Triple<Long, Int, PageLock>>,
    ): SearchPosition? {
        if (!boundGiven) {
            // No condition on this direction's seek side — skip key comparison, go straight to
            // the tree's edge (leftmost/rightmost).
            val pageId =
                if (direction == ScanDirection.FORWARD) findLeftMostLeafPageId(lockManager)
                else findRightMostLeafPageId(lockManager)
            return pageId?.let { SearchPosition(it, null) }
        }
        if (key == null) {
            // serializeUpper couldn't produce a successor — that group is already the tree's
            // rightmost edge.
            if (direction == ScanDirection.BACKWARD) {
                // Upper-bound seek: the edge group itself is the seek target.
                val pageId = findRightMostLeafPageId(lockManager)
                return pageId?.let { SearchPosition(pageId, null) }
            } else {
                // Lower-bound seek (exclusive): nothing sorts past the tree's end, so no result.
                return null
            }
        }
        // First slot with key >= boundary (lower-bound). FORWARD's result is already the answer;
        // BACKWARD's result points one slot past it, hence the -1.
        val (pageId, keyIdx, isExist) =
            searchLeafNode(key, null, traceNode, lockManager, BTreeOptMode.SELECT)
        val idx = if (direction == ScanDirection.FORWARD) keyIdx else keyIdx - 1
        return SearchPosition(pageId, idx)
    }

    /**
     * Traverse all the leaf nodes from left to right and return key, value of leaf node.
     *
     * @return Key, Value of the leaf node.
     * @see findLeftMostLeafPageId
     */
    fun traverse(): List<Pair<ByteArray, ByteArray>> {
        val result = mutableListOf<Pair<ByteArray, ByteArray>>()
        val lockManager = LockManager(LockMode.READ)
        var leafNodePageIdCursor: Long? = findLeftMostLeafPageId(lockManager) ?: return emptyList()
        lockManager.push(storageManager.fetchPage(leafNodePageIdCursor!!, LockMode.READ))
        while (true) {
            val currentLock = lockManager.last
            var isSafeToUnlockAncestor = false
            val nextLeafNodePageId = currentLock.asReadView { buffer ->
                val page = SlottedPage(indexConfig, leafNodePageIdCursor!!, buffer)
                val currentNode = Node.from(indexConfig, page)
                isSafeToUnlockAncestor = currentNode.isSafeNode(BTreeOptMode.SELECT)
                val keys = currentNode.keyView
                val values = currentNode.valueView
                for (idx in keys.indices) {
                    val key = keys[idx]
                    val value = values[idx]
                    result += key to value
                }
                page.rightSiblingPageId
            }
            if (isSafeToUnlockAncestor) lockManager.releaseAncestor(currentLock)
            currentLock.close()
            if (nextLeafNodePageId == INVALID_PAGE_ID) break
            val nextLock = storageManager.fetchPage(nextLeafNodePageId, LockMode.READ)
            lockManager.push(nextLock)
            leafNodePageIdCursor = nextLeafNodePageId
        }
        lockManager.close()
        return result
    }

    private fun checkOverflowAndSplitFirst(
        node: Node,
        page: SlottedPage,
        key: ByteArray,
        value: ByteArray,
        lockManager: LockManager,
        traceNode: Stack<Triple<Long, Int, PageLock>>,
    ) {
        if (node.wouldOverflow(key, value)) {
            val separatorKey = page.getData(node.promotionKeyIdx() + 1).first
            try {
                val currentLockSize = lockManager.size
                split(traceNode, lockManager)
                if (Arrays.compareUnsigned(key, separatorKey) >= 0) {
                    val rightLeafPageLock = lockManager.at(currentLockSize)
                    rightLeafPageLock.asWriteView { rightLeafBuffer ->
                        val rightPage =
                            SlottedPage(indexConfig, rightLeafPageLock.pageId, rightLeafBuffer)
                        val rightNode = Node.from(indexConfig, rightPage) as LeafNode
                        rightNode.insert(key, value)
                    }
                } else {
                    node.insert(key, value)
                }
            } catch (e: Exception) {
                lockManager.close()
                throw e
            }
        } else {
            node.insert(key, value)
        }
    }

    private fun checkUnderflow(
        node: Node,
        leafNodePageId: Long,
    ): Boolean = node.isUnderflow && (leafNodePageId != rootPageId || node.keyCount == 0)

    /**
     * After deleting the first key of a leaf (no underflow), walk up the traceNode stack and update
     * the first ancestor separator that points to the leaf's subtree as a non-leftmost child. This
     * keeps the strict B+tree separator invariant: `sep.at(i) == leftmostLeafFirstKey(child[i+1])`
     */
    private fun propagateSeparatorUpdate(
        traceNode: Stack<Triple<Long, Int, PageLock>>,
        newFirstKey: ByteArray,
        lockManager: LockManager,
    ) {
        val stackList = traceNode.toList()
        for (i in stackList.size - 1 downTo 1) {
            val childIdx = stackList[i].second
            if (childIdx > 0) {
                val parentTrace = stackList[i - 1]
                val lock =
                    if (parentTrace.third.isWriteLocked) {
                        parentTrace.third
                    } else {
                        val refetchedLock =
                            storageManager.fetchPage(parentTrace.first, LockMode.WRITE)
                        lockManager.push(refetchedLock)
                        refetchedLock
                    }
                lock.asWriteView { parentBuffer ->
                    val parentPage = SlottedPage(indexConfig, parentTrace.first, parentBuffer)
                    val parentNode = Node.from(indexConfig, parentPage) as InternalNode
                    parentNode.updateKey(childIdx - 1, newFirstKey)
                }
                return
            }
        }
    }

    /**
     * Handle underflow by following steps.
     *
     * Redistribution
     *
     * @see LeafNode.redistribute
     * @see InternalNode.redistribute
     *     - The minimum key of the right node became a new separate key.
     *
     * Merge
     *
     * @see LeafNode.merge
     * @see InternalNode.merge
     *     - The right node will be merged into the left node.
     *
     * Should rebalance continuously to the root node.
     */
    private fun handleUnderflow(
        traceNode: Stack<Triple<Long, Int, PageLock>>,
        lockManager: LockManager,
    ) {
        var currentTrace = traceNode.pop()

        var currentPageId: Long = currentTrace.first
        var keyIdx: Int = currentTrace.second
        var currentLock: PageLock = currentTrace.third
        var isRoot: Boolean = traceNode.isEmpty()
        var isUnderflow = true

        while (!isRoot && isUnderflow) {
            val nextTrace = traceNode.peek()
            val parentLock =
                if (nextTrace.third.isWriteLocked) {
                    nextTrace.third
                } else {
                    val refetchedLock = storageManager.fetchPage(nextTrace.first, LockMode.WRITE)
                    lockManager.push(refetchedLock)
                    refetchedLock
                }
            var isDone = false

            currentLock.asWriteView { currentBuffer ->
                val currentPage = SlottedPage(indexConfig, currentPageId, currentBuffer)
                val currentNode = Node.from(indexConfig, currentPage)
                isUnderflow = currentNode.isUnderflow

                if (isUnderflow) {
                    parentLock.asWriteView { parentBuffer ->
                        val parentPage = SlottedPage(indexConfig, nextTrace.first, parentBuffer)
                        val parentNode = Node.from(indexConfig, parentPage) as InternalNode
                        val leftSiblingPageId =
                            try {
                                parentNode.childPageId(keyIdx - 1)
                            } catch (_: StorageEngineException.SlotOutOfBound) {
                                null
                            }
                        val rightSiblingPageId =
                            try {
                                parentNode.childPageId(keyIdx + 1)
                            } catch (_: StorageEngineException.SlotOutOfBound) {
                                null
                            }
                        val siblingPageIds = listOf(leftSiblingPageId, rightSiblingPageId)
                        val siblingLocks = mutableListOf<PageLock>()
                        for (siblingId in siblingPageIds) {
                            if (siblingId != null && !isDone) {
                                val siblingLock =
                                    storageManager.fetchPage(siblingId, lockManager.lockMode)
                                lockManager.push(siblingLock)
                                siblingLocks.add(siblingLock)
                                siblingLock.asWriteView { siblingBuffer ->
                                    val siblingPage =
                                        SlottedPage(indexConfig, siblingId, siblingBuffer)
                                    val siblingNode = Node.from(indexConfig, siblingPage)
                                    if (siblingNode.hasSurplusKey) {
                                        currentNode.redistribute(siblingNode, parentNode, keyIdx)
                                        isDone = true
                                    }
                                }
                            }
                        }
                        var isMerged = false
                        if (!isDone) {
                            /*
                             * merge 후에 right node 삭제 처리
                             * leaf node의 경우 sibling 재연결 처리 필요
                             * */
                            for (siblingLock in siblingLocks) {
                                if (!isMerged) {
                                    siblingLock.asWriteView { siblingBuffer ->
                                        val siblingPage =
                                            SlottedPage(
                                                indexConfig,
                                                siblingLock.pageId,
                                                siblingBuffer,
                                            )
                                        val siblingNode = Node.from(indexConfig, siblingPage)
                                        val (_, rightPageId) =
                                            currentNode.merge(siblingNode, parentNode, keyIdx)
                                        isMerged = true
                                        val victimPageLock: PageLock =
                                            if (rightPageId == currentLock.pageId) currentLock
                                            else siblingLock
                                        lockManager.closeAndRemoveLock(victimPageLock)
                                        storageManager.deletePage(rightPageId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isDone) break
            currentTrace = traceNode.pop()
            currentPageId = currentTrace.first
            keyIdx = currentTrace.second
            isRoot = traceNode.isEmpty()
            currentLock = parentLock
        }

        if (isRoot) {
            var needChangeRoot = false
            var newRootId: Long? = null
            currentLock.asReadView { buffer ->
                val page = SlottedPage(indexConfig, currentPageId, buffer)
                val node = Node.from(indexConfig, page)
                when {
                    node is InternalNode && node.keyCount == 0 -> {
                        newRootId = node.childPageId(0)
                        needChangeRoot = true
                    }

                    node.isLeaf && node.keyCount == 0 -> {
                        newRootId = INVALID_PAGE_ID
                        needChangeRoot = true
                    }
                }
            }
            if (needChangeRoot && newRootId != null) {
                changeRootPageId(newRootId)
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
     */
    private fun split(
        traceNode: Stack<Triple<Long, Int, PageLock>>,
        lockManager: LockManager,
    ) {
        var continueLoop = true
        while (traceNode.isNotEmpty() && continueLoop) {
            val (currentPageId, currentSlotIdx, currentPageLock) =
                try {
                    traceNode.pop()
                } catch (e: EmptyStackException) {
                    throw IndexException.InvalidTraceStack(
                        EngineErrorDetail(
                            reason =
                                "Unexpected node trace data invalid. IndexName: $name TargetTableName: $targetTable"
                        ),
                        e,
                    )
                }
            var newPageId: Long = INVALID_PAGE_ID
            if (currentPageLock.pageId != currentPageId) {
                throw IndexException.InvalidTraceObject(
                    EngineErrorDetail(
                        pageId = currentPageId,
                        reason =
                            "The provided trace object does not match the most recently pushed lock in the latch queue.",
                    )
                )
            }

            currentPageLock.asWriteView { buffer ->
                val page = SlottedPage(indexConfig, currentPageId, buffer)
                val node = Node.from(indexConfig, page)

                // InternalNode: only split if it actually overflowed after receiving the promotion
                // key
                if (node is InternalNode && !node.isOverflow) {
                    continueLoop = false
                    return@asWriteView
                }

                val nodeSplitData =
                    when (node) {
                        is LeafNode -> {
                            val nodeSplitData = node.split()
                            val newLock =
                                storageManager.newPage(PageType.LEAF_NODE, lockManager.lockMode)
                            lockManager.push(newLock)
                            newLock.asWriteView { newBuffer ->
                                newPageId = newLock.pageId
                                val newPage = SlottedPage(indexConfig, newPageId, newBuffer)
                                val newNode = Node.from(indexConfig, newPage) as LeafNode
                                newNode.appendAllData(
                                    nodeSplitData.splitKeys,
                                    nodeSplitData.splitValues,
                                )
                                node.linkNewSiblingNode(newNode)
                                nodeSplitData
                            }
                        }

                        is InternalNode -> {
                            val nodeSplitData = node.split()
                            val newLock =
                                storageManager.newPage(PageType.INTERNAL_NODE, lockManager.lockMode)
                            lockManager.push(newLock)
                            newLock.asWriteView { newBuffer ->
                                newPageId = newLock.pageId
                                val newPage = SlottedPage(indexConfig, newPageId, newBuffer)
                                val newNode = Node.from(indexConfig, newPage) as InternalNode
                                newPage.leftMostChildPageId = nodeSplitData.leftMostChildPageId
                                newNode.appendAllData(
                                    nodeSplitData.splitKeys,
                                    nodeSplitData.splitValues,
                                )
                                nodeSplitData
                            }
                        }

                        else -> {
                            throw IndexException.InvalidNodeType(
                                EngineErrorDetail(
                                    pageType = node.page.type,
                                    reason = "Invalid node type",
                                )
                            )
                        }
                    }
                if (traceNode.isEmpty()) {
                    val newLock = storageManager.newPage(PageType.INTERNAL_NODE, LockMode.WRITE)
                    lockManager.push(newLock)
                    newLock.asWriteView { newBuffer ->
                        val newRootPageId = newLock.pageId
                        val newRootPage = SlottedPage(indexConfig, newRootPageId, newBuffer)
                        val newRootNode = Node.from(indexConfig, newRootPage) as InternalNode
                        newRootPage.leftMostChildPageId = currentPageId
                        newRootNode.insert(
                            nodeSplitData.promotionKey,
                            pageIDSerializer.serialize(newPageId),
                        )
                        changeRootPageId(newRootPageId)
                    }
                } else {
                    val parentPageId = traceNode.peek().first
                    val rootPageLock = traceNode.peek().third
                    rootPageLock.asWriteView { rootBuffer ->
                        val parentPage = SlottedPage(indexConfig, parentPageId, rootBuffer)
                        val parentNode = Node.from(indexConfig, parentPage) as InternalNode
                        parentNode.insertAt(
                            currentSlotIdx,
                            nodeSplitData.promotionKey,
                            pageIDSerializer.serialize(newPageId),
                        )
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
     * - Find the key which is greater than provided key within key 1-3 and go down to left subtree
     *   of that key.
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
     */
    private fun searchLeafNode(
        key: ByteArray,
        value: ByteArray?,
        traceNode: Stack<Triple<Long, Int, PageLock>>,
        lockManager: LockManager,
        operationMode: BTreeOptMode,
    ): Triple<Long, Int, Boolean> {
        if (rootPageId == INVALID_PAGE_ID) {
            throw IndexException.EmptyTree(
                EngineErrorDetail(
                    reason =
                        "Search function should be called when the tree is not empty. IndexName: $name TargetTableName: $targetTable"
                )
            )
        }

        var pageIdCursor: Long = rootPageId
        val rootPageLock = storageManager.fetchPage(pageIdCursor, lockManager.lockMode)
        lockManager.push(rootPageLock)
        traceNode.push(Triple(rootPageId, -1, rootPageLock))
        while (true) {
            val currentLock = lockManager.last
            var isSafeToUnlockAncestor = false
            currentLock.asReadView { buffer ->
                val currentPage = SlottedPage(indexConfig, pageIdCursor, buffer)
                val currentNode = Node.from(indexConfig, currentPage)
                if (currentNode.isSafeNode(operationMode, key, value)) isSafeToUnlockAncestor = true

                val result = currentNode.search(key)
                if (currentNode.isLeaf) {
                    val (searchIdx, isExist) = currentNode.search(key, true)
                    return Triple(pageIdCursor, searchIdx, isExist)
                } else {
                    val currentInternalNode = currentNode as InternalNode
                    val nextPageId = currentInternalNode.childPageId(result.first)
                    val nextLock = storageManager.fetchPage(nextPageId, lockManager.lockMode)
                    if (isSafeToUnlockAncestor) lockManager.releaseAncestor(currentLock)
                    lockManager.push(nextLock)
                    traceNode.push(Triple(nextPageId, result.first, nextLock))
                    pageIdCursor = nextPageId
                }
            }
        }
    }

    /**
     * Find the left most leaf of the B+tree.
     *
     * @return The left most child of B+tree.
     */
    private fun findLeftMostLeafPageId(lockManager: LockManager): Long? =
        findExtremeLeafPageId(lockManager) { it.childPageId(0) }

    /**
     * Find the right most leaf of the B+tree.
     *
     * @return The right most child of B+tree.
     */
    private fun findRightMostLeafPageId(lockManager: LockManager): Long? =
        findExtremeLeafPageId(lockManager) { it.rightMostChildPageId }

    /**
     * Descend from the root to a leaf without comparing against any key, always following the child
     * that [selectChild] picks at each internal node — e.g. the left-most or right-most one. Used
     * when there's no key to seek by (e.g. no lower/upper bound), so [searchLeafNode]'s
     * comparison-based descent doesn't apply.
     *
     * @return The left/right-most leaf page id, or null if the tree is empty.
     */
    private fun findExtremeLeafPageId(
        lockManager: LockManager,
        selectChild: (InternalNode) -> Long,
    ): Long? {
        var pageIdCursor = if (rootPageId != INVALID_PAGE_ID) rootPageId else return null
        var isLeaf = false
        lockManager.push(storageManager.fetchPage(pageIdCursor, lockManager.lockMode))
        while (true) {
            val currentPageLock = lockManager.last
            var isSafeToUnlockAncestor = false
            val nextPageId = currentPageLock.asReadView { buffer ->
                val currentPage = SlottedPage(indexConfig, pageIdCursor, buffer)
                val currentNode = Node.from(indexConfig, currentPage)
                isSafeToUnlockAncestor = currentNode.isSafeNode(BTreeOptMode.SELECT)
                if (currentNode.isLeaf) {
                    isLeaf = true
                    pageIdCursor
                } else {
                    selectChild(currentNode as InternalNode)
                }
            }
            val nextLock = storageManager.fetchPage(nextPageId, lockManager.lockMode)
            if (isSafeToUnlockAncestor) lockManager.releaseAncestor(currentPageLock)
            lockManager.push(nextLock)
            pageIdCursor = nextPageId
            if (isLeaf) break
        }
        return pageIdCursor
    }

    private fun changeRootPageId(newRootPageId: Long) {
        rootPageId = newRootPageId
        onRootChanged?.invoke(newRootPageId)
    }

    /** Print the tree with logger. Only for test. */
    fun printTree(format: (ByteArray) -> String) {
        data class QueueItem(
            val pageId: Long,
            val level: Int,
            val isLeaf: Boolean,
            val idx: Int,
            val pageLock: PageLock,
        )

        val startPageId = rootPageId
        val queue = ArrayDeque<QueueItem>()
        val lockManager = LockManager(LockMode.READ)
        val startLock = storageManager.fetchPage(startPageId, lockManager.lockMode)
        lockManager.push(startLock)
        val startNodeIsLeaf = startLock.asReadView { buffer ->
            val page = SlottedPage(indexConfig, startPageId, buffer)
            val node = Node.from(indexConfig, page)
            node.isLeaf
        }
        queue.addLast(QueueItem(startPageId, 0, startNodeIsLeaf, 0, startLock))
        var prevLevel = 0
        val viewBuilder = StringBuilder()
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            val (currentPageId, level, isLeaf, idx, currentLock) = item
            if (prevLevel != level) {
                viewBuilder.append("\n")
            }

            currentLock.asReadView { buffer ->
                val currentPage = SlottedPage(indexConfig, currentPageId, buffer)
                var currentNode = Node.from(indexConfig, currentPage)
                printNode(viewBuilder, currentNode, idx, format)
                if (!isLeaf) {
                    currentNode = currentNode as InternalNode
                    for (i in 0..<currentNode.keyCount + 1) {
                        val childNodePageId = currentNode.childPageId(i)
                        val childPageLock =
                            storageManager.fetchPage(childNodePageId, lockManager.lockMode)
                        lockManager.push(childPageLock)
                        val childIsLeaf = childPageLock.asReadView { childBuffer ->
                            val childPage = SlottedPage(indexConfig, childNodePageId, childBuffer)
                            val childNode = Node.from(indexConfig, childPage)
                            childNode.isLeaf
                        }
                        queue.addLast(
                            QueueItem(childNodePageId, level + 1, childIsLeaf, i, childPageLock)
                        )
                    }
                }
            }
            lockManager.closeAndRemoveLock(currentLock)
            prevLevel = level
        }
        lockManager.close()
        logger.info { "\n\n$viewBuilder\n\n" }
    }

    /** Print a single node. */
    private fun printNode(
        viewBuilder: StringBuilder,
        node: Node,
        idx: Int,
        format: (ByteArray) -> String,
    ) {
        val keys = node.keyView
        viewBuilder.append("   [${node.hashCode()}][$idx] ")

        for (i in keys.indices) {
            val key = keys[i]
            viewBuilder.append(format(key))
            viewBuilder.append(" ")
        }
    }
}
