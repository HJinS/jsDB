package storageEngine.frameManagement

interface ReplacementPolicy {
    fun evict(): Int?
    fun add(frameId: Int)
    fun remove(frameId: Int)
}