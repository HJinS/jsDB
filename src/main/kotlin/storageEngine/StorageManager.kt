package storageEngine

import storageEngine.page.PageLock
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
import storageEngine.util.LockMode
import storageEngine.exception.StorageManagerException
import config.IndexConfig

class StorageManager(
    private val freeSpaceManager: FreeSpaceManager,
    private val bufferPoolManager: BufferPoolManager,
    private val indexConfig: IndexConfig

){
    /**
     * [FreeSpaceManager]에서 새로운 PageID(FreePageID)를 받아와야함
     * 그 후 [BufferPoolManager]에 새로운 페이지 요청
     * Page를 SlottedPage의 형식에 맞게 초기화초기화
     * */
    fun newPage(pageType: PageType, lockMode: LockMode): PageLock{
        val freePageID = freeSpaceManager.getFreePageID()
        val newPageLock = bufferPoolManager.newPage(freePageID)
         
        newPageLock.asWriteView { newBuffer ->
            val page = SlottedPage(indexConfig, freePageID, newBuffer)
            page.initData()
            page.type = pageType
        }
        if(lockMode == LockMode.READ){
            newPageLock.downgradeLock()
        }
        return newPageLock
    }


    /**
     * [BufferPoolManager.fetchPage]로 page fetch
     *
     * */
    fun fetchPage(pageId: Long, lockMode: LockMode): PageLock{
        if(pageId <= 0L) throw StorageManagerException.InvalidPageIdException(pageId)
        val pageLock = bufferPoolManager.fetchPage(pageId, lockMode)
        pageLock.asReadView { buffer ->
            val page = SlottedPage(indexConfig, pageId, buffer)
            if(!(page.type == PageType.INTERNAL_NODE || page.type == PageType.LEAF_NODE)) throw StorageManagerException.InvalidPageTypeException(pageId, page.type)
        }
        return pageLock
    }

    /**
     * [FreeSpaceManager.addFreePageID] 를 통해 새로운 freePage로 등록
     * 순서 중요:
     * 1. addFreePageID  — page가 buffer pool에 있는 동안 free list 포인터 기록
     * 2. flushPage      — 포인터를 디스크에 flush (getFreePageID가 나중에 disk read로 읽어야 하므로)
     * 3. deletePage     — frame 회수
     * */
    fun deletePage(pageId: Long){
        if(pageId <= 0L) throw StorageManagerException.InvalidPageIdException(pageId)
        freeSpaceManager.addFreePageID(pageId)
        bufferPoolManager.flushPage(pageId)
        bufferPoolManager.deletePage(pageId)
    }
}
