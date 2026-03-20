package storageEngine

import storageEngine.page.PageHandle

class StorageManager(

){
    /**
     * [FreeSpaceManager]에서 새로운 PageID(FreePageID)를 받아와야함
     * 그 후 [BufferPoolManager]에 새로운 페이지 요청
     * Page를 SlottedPage의 형식에 맞게 초기화초기화
     * */
    fun newPage(): PageHandle{

    }


    /**
     * [BufferPoolManager.fetchPage]로 page fetch
     *
     * */
    fun fetchPage(pageId: Long): PageHandle {

    }

    /**
     * [FreeSpaceManager.addFreePageID] 를 통해 새로운 freePage로 등록
     * pin 카운터 해제, 디스크 flush 없이 즉시 삭제
     * */
    fun deletePage(pageId: Long){

    }
}