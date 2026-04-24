package storageEngine.exception

sealed class LRUException(message: String?, cause: Throwable?): StorageEngineException(message, cause) {
    class LRUListFullException(capacity: Int, youngCount: Int, oldCount: Int, cause: Throwable?=null):
        LRUException("Eviction should be called when lru list is full: young: $youngCount, old: $oldCount, capacity: $capacity", cause)

    class BufferPoolExhaustedException(capacity: Int, cause: Throwable?=null): 
        LRUException("Buffer pool is full and no pages can be evicted. capacity: $capacity", cause)
}
