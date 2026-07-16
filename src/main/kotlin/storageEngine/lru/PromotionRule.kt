package storageEngine.lru

import java.lang.System.currentTimeMillis

class PromotionRule(
    private val lruOldBlocksTimeMs: Long,
    private val clock: () -> Long = ::currentTimeMillis
) {
    fun isPromotable(node: LRUNode): Boolean {
        return clock() - node.lastAccessTime > lruOldBlocksTimeMs
    }
}