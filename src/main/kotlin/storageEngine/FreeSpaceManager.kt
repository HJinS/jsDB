package storageEngine

import storageEngine.util.INVALID_PAGE_ID
import storageEngine.util.META_PAGE_ID
import storageEngine.util.MetaPageOffset
import storageEngine.util.PageHeaderOffset
import storageEngine.util.LockMode

class FreeSpaceManager (
    private val bufferPoolManager: BufferPoolManager
){

    fun getFreePageID(): Long{
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.WRITE)
        var freePageID: Long = -1L
        pageLock.asWriteView { buffer ->
            val freeListHeadPageID = buffer.getLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset)
            freePageID = if(freeListHeadPageID != INVALID_PAGE_ID){
                val freePageLock = bufferPoolManager.fetchPage(freeListHeadPageID,  LockMode.WRITE)
                val nextFreePageID = freePageLock.asReadView { freeBuffer ->
                    // 삭제된 빈 page의 LEFT_SIBLING_PAGE_ID를 사용해서 free page 기록
                    freeBuffer.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)
                }
                buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, nextFreePageID)
                freePageLock.close()
                freeListHeadPageID
            } else{
                val nextPageID = buffer.getLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset)
                buffer.putLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset, nextPageID + 1)
                nextPageID
            }
        }
        pageLock.close()
        return freePageID
    }

    fun addFreePageID(newFreePageID: Long){
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.WRITE)
        pageLock.asWriteView { buffer ->
            val freeListHeadPageID = buffer.getLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset)
            buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, newFreePageID)
            // page 있음
            // 앞에서 page 삭제할때 해당 page를 기본적으로 초기화 시켜 주어야 함.
            if(freeListHeadPageID != INVALID_PAGE_ID){
                val newFreePageLock = bufferPoolManager.fetchPage(newFreePageID, LockMode.WRITE)
                newFreePageLock.asWriteView { newFreeBuffer ->
                    newFreeBuffer.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, freeListHeadPageID)
                }
                newFreePageLock.close()
            }
            val totalPageCount = buffer.getLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset)
            buffer.putLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset, totalPageCount - 1)
        }
        pageLock.close()
    }
}
