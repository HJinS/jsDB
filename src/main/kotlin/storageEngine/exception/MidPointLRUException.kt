package storageEngine.exception

sealed class MidPointLRUException(message: String?, cause: Throwable?): LRUException(message, cause){
    class IllegalPinStateException(frameId: Int, cause: Throwable?): MidPointLRUException("Attempt  to pin/unpin non-existent frameId: $frameId", cause)

    class BufferPoolExhaustedException(capacity: Int, cause: Throwable?): MidPointLRUException("Buffer pool is full. capacity: $capacity", cause)
}