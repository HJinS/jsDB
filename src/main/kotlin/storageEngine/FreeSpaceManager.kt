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
        var freePageID: Long = INVALID_PAGE_ID
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
                val nextPageID = buffer.getLong(MetaPageOffset.NEXT_PAGE_ID.offset)
                buffer.putLong(MetaPageOffset.NEXT_PAGE_ID.offset, nextPageID + 1)
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
            val newFreePageLock = bufferPoolManager.fetchPage(newFreePageID, LockMode.WRITE)
            newFreePageLock.asWriteView { newFreeBuffer ->
                newFreeBuffer.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, freeListHeadPageID)
            }
            newFreePageLock.close()
        }
        pageLock.close()
    }
}
