package storageEngine.util

enum class PageType(val value: Short){
    INTERNAL_NODE(0),
    LEAF_NODE(1),
    FREE_LIST(2);

    companion object {
        private val map = entries.associateBy{ it.value }
        fun fromValue(value: Short) = map[value]
    }
}


enum class PageHeaderOffset(val offset: Int, val bytes: Int){
    PAGE_TYPE(0, 1),
    RESERVED(1, 1),
    RECORD_COUNT(2, 2),
    FREE_SPACE_START(4, 2),
    FREE_SPACE_END(6, 2),
    PARENT_PAGE_ID(8, 8),
    LEFT_SIBLING_PAGE_ID(16, 8),
    RIGHT_SIBLING_PAGE_ID(24, 8),
    LSN(32, 8)
}