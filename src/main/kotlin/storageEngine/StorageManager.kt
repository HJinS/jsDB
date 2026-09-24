package storageEngine

import config.IndexConfig
import exception.StorageEngineException
import storageEngine.page.PageLock
import storageEngine.page.SlottedPage
import util.EngineErrorDetail
import util.LockMode
import util.PageType

/**
 * Thin layer over [BufferPoolManager]/[FreeSpaceManager] for the B+Tree: page allocation/fetch/
 * reclamation, plus the one invariant [BufferPoolManager] itself doesn't know about — that every
 * page it hands out here is a live [SlottedPage] node ([PageType.INTERNAL_NODE]/`LEAF_NODE`).
 */
class StorageManager(
    private val freeSpaceManager: FreeSpaceManager,
    private val bufferPoolManager: BufferPoolManager,
    private val indexConfig: IndexConfig,
) {
    /**
     * Gets a new page id (FreePageID) from [FreeSpaceManager], requests a new page from
     * [BufferPoolManager], then initializes it in [SlottedPage]'s format.
     */
    fun newPage(pageType: PageType, lockMode: LockMode): PageLock {
        val freePageID = freeSpaceManager.getFreePageID()
        val newPageLock = bufferPoolManager.newPage(freePageID)

        newPageLock.asWriteView { newBuffer ->
            val page = SlottedPage(indexConfig, freePageID, newBuffer)
            page.initData()
            page.type = pageType
        }
        if (lockMode == LockMode.READ) {
            newPageLock.downgradeLock()
        }
        return newPageLock
    }

    /**
     * Fetches the page via [BufferPoolManager.fetchPage], then verifies it's actually a live B+Tree
     * node (`INTERNAL_NODE`/`LEAF_NODE`) — closing the lock before throwing if not, so a failed
     * type check doesn't leak the lock/pin (issue #58).
     */
    fun fetchPage(pageId: Long, lockMode: LockMode): PageLock {
        if (pageId <= 0L)
            throw StorageEngineException.InvalidPageId(
                EngineErrorDetail(
                    pageId = pageId,
                    reason = "Attempt to fetch invalid page",
                )
            )
        val pageLock = bufferPoolManager.fetchPage(pageId, lockMode)
        var needToThrow = false
        val pageType = pageLock.asReadView { buffer ->
            val page = SlottedPage(indexConfig, pageId, buffer)
            if (!(page.type == PageType.INTERNAL_NODE || page.type == PageType.LEAF_NODE))
                needToThrow = true
            page.type
        }
        if (needToThrow) {
            pageLock.close()
            throw StorageEngineException.InvalidPageType(
                EngineErrorDetail(
                    pageId = pageId,
                    pageType = pageType,
                    reason = "Incompatible page type",
                )
            )
        }
        return pageLock
    }

    /**
     * Registers the page as a new free page via [FreeSpaceManager.addFreePageID]. Order matters:
     * 1. addFreePageID — records the free-list pointer while the page is still in the buffer pool.
     * 2. flushPage — flushes that pointer to disk (since [FreeSpaceManager.getFreePageID] will
     *    later need to read it back from disk).
     * 3. deletePage — reclaims the frame.
     */
    fun deletePage(pageId: Long) {
        if (pageId <= 0L)
            throw StorageEngineException.InvalidPageId(
                EngineErrorDetail(
                    pageId = pageId,
                    reason = "Attempt to fetch invalid page",
                )
            )
        freeSpaceManager.addFreePageID(pageId)
        bufferPoolManager.flushPage(pageId)
        bufferPoolManager.deletePage(pageId)
    }
}
