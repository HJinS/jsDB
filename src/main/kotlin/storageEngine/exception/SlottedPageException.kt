package storageEngine.exception

import storageEngine.util.PageType

sealed class SlottedPageException(message: String?, cause: Throwable?): StorageEngineException(message, cause){
    class DuplicatedKeyException(
        pageId: Long, pageType: PageType, cause: Throwable?=null
    ): SlottedPageException("Already exists. pageId: $pageId type: $pageType", cause)

    class SlotOutOfBoundException(
        slotId: Int, pageId: Long, pageType: PageType, cause: Throwable?=null
    ): SlottedPageException("No more data. slotID: $slotId pageID: $pageId pageType: $pageType", cause)

    class SlotShiftException(
        pageId: Long, pageType: PageType, cause: Throwable?=null
    ): SlottedPageException("Invalid shift count. pageID: $pageId pageType: $pageType", cause)

    class PageFullException(
        dataLength: Int, pageId: Long, cause: Throwable?=null
    ): SlottedPageException("Page full maybe too large record data: $dataLength - pageID: $pageId,", cause)
}
