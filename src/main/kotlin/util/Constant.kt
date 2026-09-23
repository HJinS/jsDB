package util

/** The single-byte tag stored at [PageHeaderOffset.PAGE_TYPE]. */
enum class PageType(val value: Byte){
    EMPTY(0),
    INTERNAL_NODE(1),
    LEAF_NODE(2);

    companion object {
        private val map = entries.associateBy{ it.value }
        fun fromValue(value: Byte) = map[value]
    }
}


/**
 * Byte layout of the fixed page header every B+Tree node page starts with (56 bytes total, see
 * [storageEngine.page.Page.HEADER_SIZE]) - see [storageEngine.page.SlottedPage]'s class doc for
 * the full annotated table and an ASCII diagram of what follows the header.
 * */
enum class PageHeaderOffset(val offset: Int){
    PAGE_ID(0),
    PAGE_TYPE(8),
    RESERVED_ONE(9),
    RECORD_COUNT(10),
    FREE_SPACE_START(12),
    FREE_SPACE_END(14),
    RESERVED_THREE(16),
    RESERVED_TWO(18),
    PARENT_PAGE_ID(24),
    LEFT_SIBLING_PAGE_ID(32),
    RIGHT_SIBLING_PAGE_ID(40),
    LSN(48)
}

/**
 * Byte layout of the meta page ([META_PAGE_ID], page 0) - see [storageEngine.MetaPageManager].
 * Every field is an 8-byte `Long`.
 * */
enum class MetaPageOffset(val offset: Int){
    FREE_LIST_HEAD_PAGE_ID(0),
    NEXT_PAGE_ID(8),
    TABLE_CATALOG_ROOT_PAGE_ID(16),
    COLUMN_CATALOG_ROOT_PAGE_ID(24),
    INDEX_CATALOG_ROOT_PAGE_ID(32),
    NEXT_TABLE_ID(40),
    NEXT_INDEX_ID(48)
}

/** Fixed page id of the meta page - see [storageEngine.MetaPageManager]. */
const val META_PAGE_ID = 0L

/** Sentinel for "no page" (an empty tree's `rootPageId`, a leaf's missing sibling, a freed page's own id, etc). */
const val INVALID_PAGE_ID = -2L

/** First page id a brand-new database file hands out (page 0 is reserved for [META_PAGE_ID]). */
const val START_PAGE_ID = 1L

/** Which [java.util.concurrent.locks.ReentrantReadWriteLock] side a [storageEngine.page.PageLock] acquires. */
enum class LockMode {WRITE, READ}

/** `String.format` pattern for a table's auto-generated primary index name when none is given - see `DataBase.createTable`. */
const val PRIMARY_KEY_IDX_NAME_PREFIX = "%s_PK_IDX"
