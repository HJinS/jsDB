package storageEngine.exception

import storageEngine.util.PageType

sealed class StorageManagerException(message: String?, cause: Throwable?): StorageEngineException(message, cause){
    class InvalidPageIdException(
        pageId: Long, cause: Throwable?
    ): StorageEngineException("Attempt  to fetch invalid page pageId: $pageId", cause)

    class InvalidPageTypeException(
        pageId: Long, pageType: PageType, cause: Throwable?
    ): StorageEngineException("Incompatible page type: Cannot fetch page $pageId of $pageType", cause)
}

