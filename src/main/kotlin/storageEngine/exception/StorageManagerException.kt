package storageEngine.exception

import storageEngine.util.PageType

sealed class StorageManagerException(message: String?, cause: Throwable?): StorageEngineException(message, cause){
    class InvalidPageIdException(
        pageId: Long, cause: Throwable?=null
    ): StorageManagerException("Attempt to fetch invalid page pageId: $pageId", cause)

    class InvalidPageTypeException(
        pageId: Long, pageType: PageType, cause: Throwable?=null
    ): StorageManagerException("Incompatible page type: Cannot fetch page $pageId of $pageType", cause)

    class PageNotFoundException(
        pageId: Long, cause: Throwable?=null
    ): StorageManagerException("Page $pageId does not exist in storage", cause)
}
