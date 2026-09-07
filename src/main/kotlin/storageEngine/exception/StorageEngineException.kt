package storageEngine.exception

import util.MetaPageOffset
import util.PageType

sealed class StorageEngineException(message: String?, cause: Throwable? = null): RuntimeException(message, cause) {
    // DiskManager
    class InvalidReadOffsetException(
        pageId: Long, cause: Throwable? = null
    ): StorageEngineException("PageId: $pageId not exist", cause)

    class FileCorruptedException(
        fileSize: Long, pageSize: Int, cause: Throwable? = null
    ): StorageEngineException("File size $fileSize is not a multiple of pageSize $pageSize.", cause)

    // StorageManager
    class InvalidPageIdException(
        pageId: Long, cause: Throwable? = null
    ): StorageEngineException("Attempt to fetch invalid page pageId: $pageId", cause)

    class InvalidPageTypeException(
        pageId: Long, pageType: PageType, cause: Throwable? = null
    ): StorageEngineException("Incompatible page type: Cannot fetch page $pageId of $pageType", cause)

    // LRU
    class LRUEvictException(
        capacity: Int, youngCount: Int, oldCount: Int, cause: Throwable? = null
    ): StorageEngineException(
        "Could not evict frame. May be all frame is pinned or buffer pool is empty." +
            " young: $youngCount, old: $oldCount, capacity: $capacity", cause
    )

    // BufferPoolManager
    class PageNotFoundInCacheException(
        pageId: Long, cause: Throwable? = null
    ): StorageEngineException("Unable to find page in buffer pool pageId: $pageId", cause)

    class PageInUseException(
        pageId: Long, cause: Throwable? = null
    ): StorageEngineException("Page $pageId is currently in use (pin count > 0) and cannot be deleted.", cause)

    class UnExpectedException(
        pageId: Long, cause: Throwable? = null
    ): StorageEngineException("Something went wrong. Maybe buffer full exhausted pageId: $pageId.", cause)

    // SlottedPage
    class SlotOutOfBoundException(
        slotId: Int, pageId: Long, pageType: PageType, cause: Throwable? = null
    ): StorageEngineException("No more data. slotID: $slotId pageID: $pageId pageType: $pageType", cause)

    class SlotShiftException(
        pageId: Long, pageType: PageType, cause: Throwable? = null
    ): StorageEngineException("Invalid shift count. pageID: $pageId pageType: $pageType", cause)

    class PageFullException(
        dataLength: Int, pageId: Long, cause: Throwable? = null
    ): StorageEngineException("Page full maybe too large record data: $dataLength - pageID: $pageId,", cause)

    class InvalidMetaArgument(
        metaPageOffset: MetaPageOffset, cause: Throwable? = null
    ): StorageEngineException("Invalid argument: $metaPageOffset,", cause)
}
