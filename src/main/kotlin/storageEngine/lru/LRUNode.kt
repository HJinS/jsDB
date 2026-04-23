package storageEngine.lru

import java.lang.System.currentTimeMillis

class LRUNode(
    val frameId: Int,
    var lastAccessTime: Long = currentTimeMillis()
) {
    var next: LRUNode? = null
    var prev: LRUNode? = null
    var isOld: Boolean = true
    var isPinned: Boolean = false

    fun resetLink(){
        next = null
        prev = null
    }
}
