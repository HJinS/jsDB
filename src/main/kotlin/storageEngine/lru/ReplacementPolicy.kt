package storageEngine.lru

/**
 * Decides which buffer pool [storageEngine.page.Frame] to evict when a new page needs one,
 * tracking frames purely by `frameId` (no knowledge of pages/disk — that's
 * [storageEngine.BufferPoolManager]'s job). The only implementation is [FrameNodePolicy]
 * (Midpoint LRU, see [GenerationalList]).
 * */
interface ReplacementPolicy {
    /**
     * Picks an eviction victim and removes it from tracking.
     *
     * @return The evicted frame id.
     * @throws exception.StorageEngineException.LRUEvict If nothing is evictable (every tracked
     *   frame is pinned, or nothing is tracked at all).
     * */
    fun evict(): Int

    /** Records a re-access to an already-pinned frame (promotion/touch bookkeeping only — pin state itself is [pin]'s job). */
    fun add(frameId: Int)

    /** Marks [frameId] as no longer pinned, making it eviction-eligible again. */
    fun unpin(frameId: Int)

    /** Marks [frameId] as pinned (in use), excluding it from [evict] until [unpin]. */
    fun pin(frameId: Int)
}