package storageEngine

import util.INVALID_PAGE_ID
import util.META_PAGE_ID
import util.MetaPageOffset
import util.PageHeaderOffset
import util.LockMode

/**
 * Tracks reusable ("freed") page ids as a singly-linked list threaded through the pages
 * themselves: each freed page's own [PageHeaderOffset.LEFT_SIBLING_PAGE_ID] field (otherwise
 * meaningless once the page is freed) is repurposed as the "next free page" pointer, and the head
 * of that chain lives in the meta page at [MetaPageOffset.FREE_LIST_HEAD_PAGE_ID]. No extra
 * on-disk structure is needed for the free list itself.
 *
 * Falls back to [MetaPageOffset.NEXT_PAGE_ID] (a monotonically increasing counter — the file's
 * high-water mark) whenever the free list is empty, i.e. growing the file is only ever the last
 * resort after reuse.
 *
 * Goes through [bufferPoolManager] directly rather than [StorageManager], since [StorageManager]'s
 * `fetchPage` rejects anything that isn't a live B+Tree node page — the meta page and freed pages
 * (whose type/content are no longer a valid node) would fail that check.
 * */
class FreeSpaceManager (
    private val bufferPoolManager: BufferPoolManager
){

    /** Pops one page id off the free list, or allocates a brand-new one if the list is empty. */
    fun getFreePageID(): Long{
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.WRITE)
        var freePageID: Long = INVALID_PAGE_ID
        pageLock.asWriteView { buffer ->
            val freeListHeadPageID = buffer.getLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset)
            freePageID = if(freeListHeadPageID != INVALID_PAGE_ID){
                val freePageLock = bufferPoolManager.fetchPage(freeListHeadPageID,  LockMode.WRITE)
                val nextFreePageID = freePageLock.asReadView { freeBuffer ->
                    // Reads the freed page's LEFT_SIBLING_PAGE_ID, repurposed as the free list's next pointer.
                    freeBuffer.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)
                }
                buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, nextFreePageID)
                freePageLock.close()
                freeListHeadPageID
            } else{
                val nextPageID = buffer.getLong(MetaPageOffset.NEXT_PAGE_ID.offset)
                buffer.putLong(MetaPageOffset.NEXT_PAGE_ID.offset, nextPageID + 1)
                nextPageID
            }
        }
        pageLock.close()
        return freePageID
    }

    /**
     * Pushes [newFreePageID] onto the front of the free list. Must be called while
     * [newFreePageID] is still resident in the buffer pool ([StorageManager.deletePage] relies on
     * this — see BUG-018 in `history/bugs/`), since this writes into that page's own header.
     * */
    fun addFreePageID(newFreePageID: Long){
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.WRITE)
        pageLock.asWriteView { buffer ->
            val freeListHeadPageID = buffer.getLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset)
            buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, newFreePageID)
            val newFreePageLock = bufferPoolManager.fetchPage(newFreePageID, LockMode.WRITE)
            newFreePageLock.asWriteView { newFreeBuffer ->
                newFreeBuffer.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, freeListHeadPageID)
            }
            newFreePageLock.close()
        }
        pageLock.close()
    }
}
