package storageEngine.exception

sealed class MidPointLRUException(message: String?, cause: Throwable?): LRUException(message, cause){
    class LRUListFullException(
        capacity: Int, youngCount: Int, oldCount: Int, cause: Throwable?
    ): MidPointLRUException(
        "Eviction should be called when lru list is full: young: $youngCount, old: $oldCount, capacity: $capacity",
        cause
    )

}
