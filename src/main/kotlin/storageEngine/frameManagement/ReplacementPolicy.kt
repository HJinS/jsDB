package storageEngine.frameManagement

interface ReplacementPolicy {
    fun evict(): Int?
    fun access(frameId: Int)
    fun pin(frameId: Int)
    fun unpin(frameId: Int)
}