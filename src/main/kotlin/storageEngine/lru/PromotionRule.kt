package storageEngine.lru

import storageEngine.exception.LRUException
import java.lang.System.currentTimeMillis

class PromotionRule(
    private val capacity: Int,
    private val lruOldBlocksTimeMs: Long
) {
    fun isPromotable(node: LRUNode): Boolean {
        val now = currentTimeMillis()
        return now - node.lastAccessTime > lruOldBlocksTimeMs
    }

    fun checkSize(currentCount: Int){
        if (currentCount >= capacity) throw LRUException.BufferPoolExhaustedException(capacity)
    }
}