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