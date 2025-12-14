package storageEngine.frameManagement

internal data class Node(
    val frameId: Int,
    var prev: Node? = null,
    var next: Node? = null,
    var isPinned: Boolean = false,
    var isOld: Boolean = true,
    var lastAccessTime: Long = 0
)