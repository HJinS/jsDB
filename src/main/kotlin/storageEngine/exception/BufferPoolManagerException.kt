package storageEngine.exception

sealed class BufferManagerException(message: String?, cause: Throwable?): StorageEngineException(message, cause){
    class PageNotFoundInCacheException(
        pageId: Long, cause: Throwable?
    ): BufferManagerException("Unable to find page in buffer pool pageId: $pageId", cause)

    class FrameNotFoundException(
        frameId: Int, cause: Throwable?
    ): BufferManagerException("Unable to find frame. Maybe frameId is wrong. frameId: $frameId", cause)

    class BufferPoolExhaustedException(
        capacity: Int, cause: Throwable?
    ): MidPointLRUException("Buffer pool is full. capacity: $capacity", cause)
}

