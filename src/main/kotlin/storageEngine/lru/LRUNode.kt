package storageEngine.lru

import java.lang.System.currentTimeMillis

/**
 * One node of the doubly-linked list [GenerationalList] manages, corresponding to one buffer pool
 * frame.
 *
 * @constructor
 * @param frameId The id of the buffer pool frame this node corresponds to. Immutable after the
 *   node is created.
 * @param lastAccessTime Set only once, at node creation, and never updated afterward on
 *   re-access (e.g. by [GenerationalList.touch]). Since this is the baseline for measuring "how
 *   long has it sat in the old region", updating it on every access would make `now -
 *   lastAccessTime ≈ 0` at the very moment of re-access, permanently failing promotion checks
 *   ([PromotionRule.isPromotable]) — a bug this project actually hit. It only naturally gets a
 *   fresh value once the frame is evicted and reused for a different page (i.e. once a new
 *   [LRUNode] instance is created).
 * */
class LRUNode(
    val frameId: Int,
    var lastAccessTime: Long = currentTimeMillis()
) {
    /** Link toward head in the [DoublyLinkedList]. */
    var next: LRUNode? = null

    /** Link toward tail in the [DoublyLinkedList]. */
    var prev: LRUNode? = null

    /** Old/young status. [GenerationalList] must update this consistently at every insert/promote/conversion point. */
    var isOld: Boolean = true

    /** While pinned, this node is excluded from [GenerationalList]'s list and can't be an eviction target. */
    var isPinned: Boolean = false

    /** Clears the previous position's link info when pinned, so re-entering the list later doesn't reference stale pointers. */
    fun resetLink(){
        next = null
        prev = null
    }
}
