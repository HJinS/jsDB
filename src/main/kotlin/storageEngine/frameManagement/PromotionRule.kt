package storageEngine.frameManagement

import storageEngine.exception.MidPointLRUException
import java.lang.System.currentTimeMillis

class PromotionRule(
    private val capacity: Int,
    private val lruOldBlocksTimeMs: Long
) {
    fun isPromotable(frame: Frame): Boolean {
        val now = currentTimeMillis()
        return now - frame.lastAccessTime > lruOldBlocksTimeMs
    }

    fun checkSize(currentCount: Int){
        if (currentCount >= capacity) throw MidPointLRUException.BufferPoolExhaustedException(capacity, null)
    }
}