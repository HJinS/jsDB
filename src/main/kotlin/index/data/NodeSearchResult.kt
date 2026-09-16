package index.data

data class NodeSearchResult (
    val pageId: Long, val searchIdx: Int, val isExist: Boolean, val isLeaf: Boolean
)