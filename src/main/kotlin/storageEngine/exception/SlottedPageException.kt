package storageEngine.exception

import storageEngine.util.PageType

open class SlottedPageException(message: String?, cause: Throwable?): StorageEngineException(message, cause){
    class DuplicatedKeyException(
        pageId: Long, pageType: PageType, cause: Throwable?
    ): SlottedPageException("Already exists. $pageId $pageType", cause)

    class SlotOutOfBoundException(
        slotId: Int, pageId: Long, pageType: PageType, cause: Throwable?
    ): SlottedPageException("No more data. slotID: $slotId pageID: $pageId pageType: $pageType", cause)
}