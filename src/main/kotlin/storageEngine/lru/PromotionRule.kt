package storageEngine.lru

import java.lang.System.currentTimeMillis

/**
 * Policy deciding whether a re-accessed old node gets promoted to young. Corresponds to InnoDB's
 * `innodb_old_blocks_time`, and covers a time-based threshold separate from the structural
 * threshold ([storageEngine.lru.GenerationalList]'s `lruOldMinLength`).
 *
 * @constructor
 * @param lruOldBlocksTimeMs How long (ms) a node must have sat in the old region before a
 *   re-access makes it eligible for promotion.
 * @param clock Function providing the current time. Defaults to [System.currentTimeMillis];
 *   injected in tests to freeze time.
 * */
class PromotionRule(
    private val lruOldBlocksTimeMs: Long,
    private val clock: () -> Long = ::currentTimeMillis
) {
    /**
     * @param node The node to check for promotion eligibility.
     * @return `true` if [lruOldBlocksTimeMs] has elapsed since `node.lastAccessTime` (set once, at creation).
     * */
    fun isPromotable(node: LRUNode): Boolean {
        return clock() - node.lastAccessTime > lruOldBlocksTimeMs
    }
}