package index.btree

import config.IndexConfig
import index.btree.node.Node
import index.data.SearchPosition
import index.serializer.KeySerializer
import index.serializer.ValueSerializer
import storageEngine.StorageManager
import storageEngine.page.SlottedPage
import util.INVALID_PAGE_ID
import util.LockMode

class Cursor<K, V>(
    private val lockManager: LockManager,
    private var currentPosition: SearchPosition,
    private val direction: ScanDirection,
    private val storageManager: StorageManager,
    private val indexConfig: IndexConfig,
    private val keySerializer: KeySerializer<K>,
    private val valueSerializer: ValueSerializer<V>,
) : AutoCloseable {
    /**
     * Advances the cursor by one entry in [direction], or returns null once there's nothing left.
     *
     * Every call lands in one of three cases:
     * - **Mid-page**: the next slot is still on the current page. Only the slot index moves;
     *   the current lock is kept for the next call.
     * - **Last slot on the page, with a neighbor**: this call still returns that slot's entry,
     *   but first hands the walk off to the neighbor - its lock is fetched and pushed *before*
     *   the current one is released, so the lock manager is never briefly empty mid-handoff.
     * - **Last slot on the page, no neighbor - or the page has nothing to read at all** (e.g. an
     *   empty page reached mid-walk): the current lock is released either way; a value is
     *   returned only if there was one to read, otherwise the loop just retries on whatever page
     *   comes next, or returns null if there's truly nowhere left to go.
     *
     * `leavingPage` and `reachedEnd` are tracked separately because "do I release this page's
     * lock" and "is the scan actually over" aren't the same question — the edge-with-a-neighbor
     * case leaves the page *and* returns a value, while the empty-page case leaves the page but
     * has nothing to return.
     */
    fun step(): Pair<K, V>? {
        while (true) {
            val currentPageId = currentPosition.pageId
            var currentLock = lockManager.last
            if (!(currentLock.isReadLocked || currentLock.isWriteLocked)) {
                currentLock = storageManager.fetchPage(currentPageId, LockMode.READ)
                lockManager.push(currentLock)
            } else if (currentLock.isWriteLocked) {
                currentLock.downgradeLock()
            }

            var reachedEnd = false
            var leavingPage = false

            val entry: Pair<ByteArray, ByteArray>? =
                currentLock.asReadView { buffer ->
                    val page = SlottedPage(indexConfig, currentPageId, buffer)
                    val node = Node.from(indexConfig, page)
                    val recordCount = page.recordCount
                    val currentSlotIdx = currentPosition.idx ?: if (direction == ScanDirection.FORWARD) 0 else recordCount - 1
                    val outOfBound = currentSlotIdx !in 0..<recordCount
                    val isEdgeData =
                        (currentSlotIdx == 0 && direction == ScanDirection.BACKWARD) ||
                            (currentSlotIdx == recordCount - 1 && direction == ScanDirection.FORWARD)

                    if (isEdgeData || outOfBound) {
                        leavingPage = true
                        val neighborPageId =
                            when (direction) {
                                ScanDirection.FORWARD -> page.rightSiblingPageId
                                ScanDirection.BACKWARD -> page.leftSiblingPageId
                            }
                        if (neighborPageId == INVALID_PAGE_ID) {
                            reachedEnd = true
                        } else {
                            val nextPosition =
                                when (direction) {
                                    ScanDirection.FORWARD -> SearchPosition(neighborPageId, 0)
                                    ScanDirection.BACKWARD -> SearchPosition(neighborPageId, null)
                                }
                            lockManager.push(storageManager.fetchPage(neighborPageId, LockMode.READ))
                            currentPosition = nextPosition
                        }
                        if (isEdgeData) node.keyView[currentSlotIdx] to node.valueView[currentSlotIdx] else null
                    } else {
                        currentPosition =
                            SearchPosition(
                                currentPageId,
                                when (direction) {
                                    ScanDirection.FORWARD -> currentSlotIdx + 1
                                    ScanDirection.BACKWARD -> currentSlotIdx - 1
                                },
                            )
                        node.keyView[currentSlotIdx] to node.valueView[currentSlotIdx]
                    }
                }

            if (leavingPage) lockManager.closeAndRemoveLock(currentLock)

            if (entry != null) {
                val (keySerialized, valueSerialized) = entry
                val key = keySerializer.deserialize(keySerialized)
                val value = valueSerializer.deserialize(valueSerialized).first
                return key to value
            }
            if (reachedEnd) return null
        }
    }

    override fun close() {
        lockManager.close()
    }
}
