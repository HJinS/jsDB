package storageEngine.exception

sealed class BufferPoolManagerException(message: String?, cause: Throwable?): StorageEngineException(message, cause){
    class PageNotFoundInCacheException(
        pageId: Long, cause: Throwable? = null
    ): BufferPoolManagerException("Unable to find page in buffer pool pageId: $pageId", cause)

    class FrameNotFoundException(
        frameId: Int, cause: Throwable? = null
    ): BufferPoolManagerException("Unable to find frame. Maybe frameId is wrong. frameId: $frameId", cause)

    class PageInUseException(
        pageId: Long, cause: Throwable? = null
    ): BufferPoolManagerException("Page $pageId is currently in use (pin count > 0) and cannot be deleted.", cause)

    class UnExpectedException(
        pageId: Long, cause: Throwable?=null
    ): BufferPoolManagerException("Something went wrong. Maybe buffer full exhausted pageId: $pageId.", cause)

}
