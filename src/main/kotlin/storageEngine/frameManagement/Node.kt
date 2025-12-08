package storageEngine.frameManagement

internal data class Node(
    val key: Int,
    var prev: Node? = null,
    var next: Node? = null,
    var isPinned: Boolean = false
)