package storageEngine

interface ReplacementPolicy {
    fun evict(): Int?
    fun access(key: Int)
    fun pin()
    fun unpin()
}