package storageEngine.frameManagement

interface ReplacementPolicy {
    fun evict(): Int?
    fun access(key: Int)
    fun pin(key: Int)
    fun unpin(key: Int)
}