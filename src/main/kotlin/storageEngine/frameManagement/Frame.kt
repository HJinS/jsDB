package storageEngine.frameManagement

data class Frame(
    val frameId: Int,
    var prev: Frame? = null,
    var next: Frame? = null,
    var isPinned: Boolean = false,
    var isOld: Boolean = true,
    var lastAccessTime: Long = 0
)