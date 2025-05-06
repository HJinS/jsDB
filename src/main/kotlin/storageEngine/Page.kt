package storageEngine

data class Page(
    val pageNumber: Int,
    var data: ByteArray,
    var isDirty: Boolean,
    var refCount: Int = 1
)
