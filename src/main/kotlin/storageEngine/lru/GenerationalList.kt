package storageEngine.lru

import exception.StorageEngineException
import util.EngineErrorDetail


/**
 * An old/young 2-segment list reproducing InnoDB's Midpoint Insertion LRU.
 *
 * Physically it's a single [DoublyLinkedList] (**head** → young → [midPoint] → old → **tail**),
 * where `midPoint` points at the old-region node closest to head (=young) — the boundary node
 * (if the young region is empty, the node right after head is itself the boundary).
 *
 * While `size < lruOldMinLength`, there's no old/young distinction at all and it acts as plain
 * LRU (InnoDB's below-`BUF_LRU_OLD_MIN_LEN` range; see `buf_LRU_add_block_low`/
 * `buf_LRU_remove_block`). This class owns `lruOldMinLength`/`youngRatio`/[PromotionRule]
 * entirely, so the caller ([storageEngine.lru.FrameNodePolicy]) doesn't need to know whether it's
 * currently plain LRU or old/young-split — it's only responsible for pin lifecycle and frameId
 * mapping, delegating all ordering/promotion decisions to this class.
 *
 * @constructor
 * @param youngRatio Target fraction (0.0–1.0) of the young region once old/young are split.
 *   [adjustRatio] multiplies this by the current `size` to recompute the cap on every call.
 * @param capacity Total frame count of the buffer pool. Not used in the old/young ratio
 *   calculation — only referenced in the exception message when [removeOldest] tries to evict
 *   from an empty list.
 * @param lruOldMinLength The threshold separating plain LRU from old/young-split state
 *   (corresponds to InnoDB's `BUF_LRU_OLD_MIN_LEN`). The moment `size` reaches this value it
 *   becomes Midpoint LRU; the moment it drops below, it reverts to plain LRU.
 * @param promotionRule Policy deciding whether a re-accessed old node gets promoted to young
 *   (used only in [touch]).
 * */
class GenerationalList(
    private val youngRatio: Double,
    private val capacity: Int,
    private val lruOldMinLength: Int,
    private val promotionRule: PromotionRule
) {
    private val linkedList = DoublyLinkedList()
    private var midPoint: LRUNode? = null

    var youngCount = 0
        private set

    var oldCount = 0
        private set

    var size = 0
        private set

    /** The tail node — next in line for [removeOldest] — or null if empty. Read-only peek; doesn't remove it. */
    val oldest
        get() = linkedList.getLast()

    /**
     * Called when an already-listed (not pinned) node is re-accessed.
     *
     * If `midPoint == null` (still plain-LRU state), there's no old/young distinction at all, so
     * the node just moves to head regardless of `node.isOld` (plain LRU's "move to top on
     * access"). Only when `midPoint != null` (old/young split) and the node is old does
     * [PromotionRule.isPromotable] check its old-region dwell time to decide whether to promote it.
     *
     * @param node The re-accessed node. Must already be in [linkedList] (i.e. not pinned).
     * */
    fun touch(node: LRUNode) {
        if(midPoint != null && node.isOld){
            if(promotionRule.isPromotable(node)) promoteYoung(node)
        } else{
            touchYoung(node)
        }
    }

    /**
     * Inserts a new node into the young region (head).
     *
     * If this insert makes `size` reach `lruOldMinLength` exactly (the same moment as InnoDB's
     * `buf_LRU_old_init`), every node accumulated so far under plain LRU is converted to old in
     * one batch.
     *
     * @param node The node to insert. Must not already be in [linkedList].
     * */
    fun addYoung(node: LRUNode){
        linkedList.addFirst(node)
        youngCount ++
        size ++
        node.isOld = false
        if(size == lruOldMinLength) markAllAsOld()
        adjustRatio()
    }

    /**
     * Inserts a new node into the old region (just before midPoint).
     *
     * If `size < lruOldMinLength`, the concept of an old region doesn't exist yet, so even if the
     * caller asked for old, this is ignored and delegated to [addYoung] instead (same as InnoDB's
     * `buf_LRU_add_block_low` ignoring its `old` argument and inserting at head when the list is
     * too short).
     *
     * @param node The node to insert. Must not already be in [linkedList].
     * */
    fun addOld(node: LRUNode){
        if(size < lruOldMinLength){
            addYoung(node)
        } else{
            val currentMidPoint = midPoint
            if(currentMidPoint == null){
                linkedList.addLast(node)
            }else {
                linkedList.add(node, currentMidPoint)
            }
            oldCount ++
            size ++
            expandOldList(node)
        }
    }

    /**
     * Pops the eviction candidate (tail). If [convertToPlainLruIfNeeded] returns true (meaning
     * this removal dropped `size` below `lruOldMinLength` and everything was already converted to
     * young), the `midPoint == node` fixup isn't needed — that result would be immediately
     * overwritten by [markAllAsYoung] anyway.
     *
     * @return The node removed from the list — the eviction victim.
     * @throws StorageEngineException.LRUEvict If the list is empty and there's nothing to evict.
     * */
    fun removeOldest(): LRUNode {
        val node = linkedList.removeLast()
            ?: throw StorageEngineException.LRUEvict(
                EngineErrorDetail(
                    reason= "Could not evict frame. May be all frame is pinned or buffer pool is empty. " +
                            "young: $youngCount, old: $oldCount, capacity: $capacity"
                )
            )
        if(node.isOld) oldCount-- else youngCount--
        size --
        if(!convertToPlainLruIfNeeded() && midPoint == node) midPoint = null
        return node
    }

    /**
     * Used when a node is removed from the list arbitrarily (e.g. for pinning). Like
     * [removeOldest], checks whether this removal should trigger reversion to plain LRU first —
     * skipping that threshold recheck just because this isn't the evict path would leave old
     * state stale specifically for this size-shrinking case. If [convertToPlainLruIfNeeded]
     * already converted everything to young, [shrinkOldList] is skipped since its result would be
     * overwritten anyway.
     *
     * @param node The node to remove from [linkedList]. Must currently be in the list.
     * */
    fun remove(node: LRUNode){
        linkedList.remove(node)
        if(node.isOld) oldCount -- else youngCount --
        size --
        if(!convertToPlainLruIfNeeded() && midPoint == node) shrinkOldList()
    }

    /**
     * If the young fraction exceeds the target, pushes midPoint toward head to widen the old
     * region.
     *
     * `maxYoungCount` is recomputed every call based on the **current `size`**, not `capacity`.
     * Using a fixed value based on `capacity` caused a bug: while the buffer pool wasn't yet full
     * (`size` ≪ `capacity`), `youngCount` could never exceed that fixed cap, making this loop
     * effectively dead code.
     * */
    private fun adjustRatio(){
        val maxYoungCount = (size * youngRatio).toInt()
        var prevMidPoint = midPoint?.prev
        while(prevMidPoint != null && youngCount > maxYoungCount){
            prevMidPoint.isOld = true
            midPoint = prevMidPoint
            youngCount --
            oldCount ++
            prevMidPoint = midPoint?.prev
        }
    }

    /**
     * When the midPoint node itself gets promoted to young, moves the boundary one step toward
     * the old side (to the next node).
     *
     * If this node was the only one left in the old region, `next` ends up being the tail
     * sentinel rather than a real old node — in that case the old region is now completely empty,
     * so the boundary must revert to `null` rather than leaving the sentinel as the boundary.
     * (At the point either `promoteYoung` or `remove` calls this, `oldCount` may or may not have
     * been decremented yet, so this checks the sentinel directly instead of relying on the count.)
     * */
    private fun shrinkOldList() {
        val next = midPoint?.next
        midPoint = if (next != null && !linkedList.isTail(next)) next else null
    }

    /**
     * The newly inserted old node is the closest one to head, so it becomes the new boundary.
     *
     * @param node The node just inserted into the old region. It becomes the new [midPoint].
     * */
    private fun expandOldList(node: LRUNode){
        midPoint = node
    }

    /**
     * If a removal drops `size` below `lruOldMinLength`, reverts to plain LRU and returns `true`.
     * The `midPoint != null` guard exists because reverting only makes sense when old/young were
     * split to begin with — a removal that happens while already in plain-LRU state
     * (`midPoint == null`) has nothing to do here.
     *
     * The return value tells the caller (`removeOldest`/`remove`) whether it should still do its
     * own individual `midPoint` fixup afterward — if this already reverted everything to young
     * (including setting `midPoint = null`), that individual fixup would just be wasted work
     * about to be overwritten, and should be skipped.
     *
     * @return `true` if this call actually reverted to plain LRU; `false` if the old/young split
     *   is still in effect.
     * */
    private fun convertToPlainLruIfNeeded(): Boolean {
        if(midPoint != null && size < lruOldMinLength){
            markAllAsYoung()
            return true
        }
        return false
    }

    /**
     * Called exactly once, at the moment of switching from plain LRU to an old/young split
     * (`size == lruOldMinLength`). Marks every node accumulated so far as old, and sets the node
     * right after head as the boundary (correct since the young region is still empty, so the
     * boundary should sit right at head). Same point/behavior as InnoDB's `buf_LRU_old_init()`.
     * */
    private fun markAllAsOld(){
        linkedList.forEach { it.isOld = true }
        oldCount = size
        youngCount = 0
        midPoint = linkedList.getFirst()
    }

    /**
     * Reverts from an old/young split back to plain LRU: marks everything young again and drops
     * the boundary. Same behavior as InnoDB's `buf_LRU_remove_block`, which clears every `old`
     * flag and resets `LRU_old` to `nullptr` when a removal drops the length below
     * `BUF_LRU_OLD_MIN_LEN`.
     * */
    private fun markAllAsYoung(){
        linkedList.forEach { it.isOld = false }
        oldCount = 0
        youngCount = size
        midPoint = null
    }

    /**
     * Promotes an old node to young. If midPoint itself is the node being promoted (i.e. it was
     * the last node left in the old region), the boundary must move first, before `remove`/
     * `addFirst` change its links — if the order were reversed, `shrinkOldList()` would operate on
     * a node that's already been moved to the young list (and lost its old link info), landing on
     * the wrong new boundary.
     *
     * @param node The old node to promote. Must currently be in the old region.
     * */
    internal fun promoteYoung(node: LRUNode){
        if(midPoint == node) shrinkOldList()
        linkedList.remove(node)
        oldCount --
        linkedList.addFirst(node)
        youngCount ++
        node.isOld = false
        adjustRatio()
    }

    /**
     * Moves node to the very front of the young list.
     *
     * When `touch()` routes here because it judged the state to be normal (plain) LRU
     * (`midPoint == null`), the re-accessed node might still have `isOld == true` left over from
     * an earlier old/young-split state. In that case, just flipping `isOld` alone would desync
     * `oldCount`/`youngCount`, so both the flag and the counts are corrected together before
     * moving the node to head.
     *
     * @param node The node to move to head. Must currently be in [linkedList].
     * */
    internal fun touchYoung(node: LRUNode){
        if(node.isOld){
            node.isOld = false
            oldCount --
            youngCount ++
        }
        linkedList.remove(node)
        linkedList.addFirst(node)
    }
}
