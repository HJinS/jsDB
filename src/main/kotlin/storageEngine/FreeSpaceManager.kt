package storageEngine

import storageEngine.util.INVALID_PAGE_ID
import storageEngine.util.META_PAGE_ID
import storageEngine.util.MetaPageOffset
import storageEngine.util.PageHeaderOffset

class FreeSpaceManager (
    private val bufferPoolManager: BufferPoolManager
){

    fun getFreePageID(): Long{
        bufferPoolManager.fetchPage(META_PAGE_ID).use{lock ->
            lock.asWriteView { buffer ->
                val freeListHeadPageID = buffer.getLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset)
                if(freeListHeadPageID != INVALID_PAGE_ID){
                    val nextFreePageID = bufferPoolManager.fetchPage(freeListHeadPageID).use { freePageLock ->
                        freePageLock.asReadView { freeBuffer ->
                            // 삭제된 빈 page의 LEFT_SIBLING_PAGE_ID를 사용해서 free page 기록
                            freeBuffer.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)
                        }
                    }
                    buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, nextFreePageID)
                    return freeListHeadPageID
                } else{
                    val nextPageID = buffer.getLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset)
                    buffer.putLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset, nextPageID + 1)
                    return nextPageID
                }
            }
        }
    }

    fun addFreePageID(newFreePageID: Long){
        bufferPoolManager.fetchPage(META_PAGE_ID).use{lock ->
            lock.asWriteView { buffer ->
                val freeListHeadPageID = buffer.getLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset)
                buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, newFreePageID)
                // page 있음
                // 앞에서 page 삭제할때 해당 page를 기본적으로 초기화 시켜 주어야 함.
                if(freeListHeadPageID != INVALID_PAGE_ID){
                    bufferPoolManager.fetchPage(newFreePageID).use { newFreePageLock ->
                        newFreePageLock.asWriteView { newFreeBuffer ->
                            newFreeBuffer.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, freeListHeadPageID)
                        }
                    }
                }
                val totalPageCount = buffer.getLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset)
                buffer.putLong(MetaPageOffset.TOTAL_PAGE_COUNT.offset, totalPageCount - 1)
            }
        }
    }
}