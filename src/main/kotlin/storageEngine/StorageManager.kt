package storageEngine

import storageEngine.page.PageHandle
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
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
    fun newPage(pageType: PageType): PageHandle{
        val freePageID = freeSpaceManager.getFreePageID()
        val newPageHandle = bufferPoolManager.newPage(freePageID)
        newPageHandle.use{ pageHandle -> 
            pageHandle.asWriteView { newBuffer ->
                val page = SlottedPage(indexConfig, freePageID, newBuffer)
                page.initData()
                page.type = pageType
            }
        }
        return newPageHandle
    }


    /**
     * [BufferPoolManager.fetchPage]로 page fetch
     *
     * */
    fun fetchPage(pageId: Long): PageHandle{
        if(pageId <= 0L) throw StorageManagerException.InvalidPageIdException(pageId, null)
        val pageHandle = bufferPoolManager.fetchPage(pageId)
        pageHandle.asReadView { buffer ->
            val page = SlottedPage(indexConfig, pageId, buffer)
            if(!(page.type == PageType.INTERNAL_NODE || page.type == PageType.LEAF_NODE)) throw StorageManagerException.InvalidPageTypeException(pageId, page.type, null)
        }
        return pageHandle
    }

    /**
     * [FreeSpaceManager.addFreePageID] 를 통해 새로운 freePage로 등록
     * pin 카운터 해제, 디스크 flush 없이 즉시 삭제
     * */
    fun deletePage(pageId: Long){
        if(pageId <= 0L) throw StorageManagerException.InvalidPageIdException(pageId, null)
        
        bufferPoolManager.deletePage(pageId) 
        freeSpaceManager.addFreePageID(pageId)
    }
}
