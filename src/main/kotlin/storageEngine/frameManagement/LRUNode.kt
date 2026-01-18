package storageEngine.frameManagement

class LRUNode (
    var frameId: Int? = null,
    var next: LRUNode? = null,
    var prev: LRUNode? = null,
    var isOld: Boolean = true,
    var lastAccessTime: Long = 0
)