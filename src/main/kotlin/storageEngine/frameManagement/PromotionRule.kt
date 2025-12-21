package storageEngine.frameManagement

import storageEngine.exception.MidPointLRUException
import java.lang.System.currentTimeMillis

class PromotionRule(
    private val capacity: Int,
    private val lruOldBlocksTimeMs: Long
) {
    fun isPromotable(node: Node): Boolean {
        val now = currentTimeMillis()
        return now - node.lastAccessTime > lruOldBlocksTimeMs
    }

    fun checkSize(currentCount: Int){
        if (currentCount >= capacity) throw MidPointLRUException.BufferPoolExhaustedException(capacity, null)
    }
}