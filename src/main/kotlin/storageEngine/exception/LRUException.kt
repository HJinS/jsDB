package storageEngine.exception

sealed class LRUException(message: String?, cause: Throwable?): StorageEngineException(message, cause) {
    class LRUEvictException(capacity: Int, youngCount: Int, oldCount: Int, cause: Throwable?=null):
        LRUException("Could not evict frame. May be all frame is pinned or buffer pool is empty." +
                " young: $youngCount, old: $oldCount, capacity: $capacity", cause)

    class BufferPoolExhaustedException(capacity: Int, cause: Throwable?=null): 
        LRUException("Buffer pool is full and no pages can be evicted. capacity: $capacity", cause)
}
