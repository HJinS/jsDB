package storageEngine.lru

interface ReplacementPolicy {
    fun evict(): Int?
    fun add(frameId: Int)
    fun unpin(frameId: Int)
    fun pin(frameId: Int)
}