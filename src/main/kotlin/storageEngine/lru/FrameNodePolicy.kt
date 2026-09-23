package storageEngine.lru

import config.MidpointLruConfig


/**
 * [ReplacementPolicy] adapter. The algorithm internals — old/young decisions, promotion, plain-LRU
 * conversion — are all delegated to [GenerationalList]; this class is only responsible for two
 * things:
 * - `frameId ↔ LRUNode` mapping ([map])
 * - pin lifecycle (create a node the first time it's pinned, exclude it from the list while
 *   pinned, restore it to its original state on unpin)
 *
 * So [add] looking like a one-line delegation is intentional — it's the result of keeping the
 * "is this currently plain LRU, is this old node promotable" logic entirely inside
 * [GenerationalList], not this class's role disappearing.
 *
 * @constructor
 * @param midpointLruConfig Config holding `youngRatio`/`capacity`/`lruOldMinLength`, passed
 *   straight through to [GenerationalList], plus `lruOldBlocksTimeMs`, used to build
 *   [PromotionRule].
 * */
class FrameNodePolicy(
    midpointLruConfig: MidpointLruConfig
): ReplacementPolicy {
    private val map = HashMap<Int, LRUNode>()
    private val generationalList:  GenerationalList = GenerationalList(
        midpointLruConfig.youngRatio,
        midpointLruConfig.capacity,
        midpointLruConfig.lruOldMinLength,
        PromotionRule(
            midpointLruConfig.lruOldBlocksTimeMs
        )
    )

    /** @return The frameId of the evicted frame. */
    override fun evict(): Int {
        val oldNode = generationalList.removeOldest()
        val frameId = oldNode.frameId
        map.remove(frameId)
        return frameId
    }

    /**
     * Re-access of a not-pinned (i.e. currently listed) node. Old/young and plain-LRU decisions
     * are entirely [GenerationalList.touch]'s job.
     *
     * @param frameId The id of the re-accessed frame. Must already be registered in [map].
     * */
    override fun add(frameId: Int) {
        val node = map[frameId]!!
        if(!node.isPinned) generationalList.touch(node)
    }

    /**
     * Returns the node to the list once unpinned. `node.isOld` still holds the position it was in
     * before being pinned, so simply calling whichever of [GenerationalList.addOld]/
     * [GenerationalList.addYoung] matches that value is enough — whether the list is currently in
     * plain-LRU state is already handled inside `addOld`, so no extra branching is needed here.
     *
     * @param frameId The id of the frame to unpin.
     * */
    override fun unpin(frameId: Int) {
        val node = map[frameId]!!
        if(node.isPinned){
            node.isPinned = false
            if(node.isOld) generationalList.addOld(node) else generationalList.addYoung(node)
        }
    }

    /**
     * For a frameId never seen before, creates a new [LRUNode] and just registers it as pinned
     * (not yet added to [GenerationalList]). For an existing (already-listed) node being pinned
     * again, removes it from the list and resets its links. The `isOld = true` it's initialized
     * with is only a provisional value — [unpin] later has [GenerationalList.addOld] re-decide it
     * based on whether the list is in plain-LRU state and overwrite it.
     *
     * @param frameId The id of the frame to pin.
     * */
    override fun pin(frameId: Int) {
        val node = map[frameId]
        if(node == null){
            map[frameId] = LRUNode(frameId).apply {
                isPinned = true
                isOld = true
            }
        } else if(!node.isPinned){
            generationalList.remove(node)
            node.resetLink()
            node.isPinned = true
        }
    }
}
