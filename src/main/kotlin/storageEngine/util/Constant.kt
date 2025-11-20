package storageEngine.util

enum class PageType(val value: Byte){
    EMPTY(0),
    INTERNAL_NODE(1),
    LEAF_NODE(2),
    FREE_LIST(3);

    companion object {
        private val map = entries.associateBy{ it.value }
        fun fromValue(value: Byte) = map[value]
    }
}


enum class PageHeaderOffset(val offset: Int, val bytes: Int){
    PAGE_ID(0, 8),
    PAGE_TYPE(8, 1),
    RESERVED_ONE(9, 1),
    RECORD_COUNT(10, 2),
    FREE_SPACE_START(12, 2),
    FREE_SPACE_END(14, 2),
    FREE_SLOT_HEAD(16, 2),
    RESERVED_TWO(18, 6),
    PARENT_PAGE_ID(24, 8),
    LEFT_SIBLING_PAGE_ID(32, 8),
    RIGHT_SIBLING_PAGE_ID(40, 8),
    LSN(48, 8)
}