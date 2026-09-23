package storageEngine.page

import config.IndexConfig
import util.INVALID_PAGE_ID
import util.PageHeaderOffset
import util.PageType
import java.nio.ByteBuffer


/**
 * The fixed-size page header shared by every B+Tree node page — see [PageHeaderOffset] for the
 * exact byte layout. [SlottedPage] builds the variable-length record area on top of this.
 * */
open class Page(
    val indexConfig: IndexConfig,
    internal val data: ByteBuffer,
    internal val pageId: Long = INVALID_PAGE_ID
){

    /** Writes a fresh, empty header into [data] — must be called once for a brand-new page (see `StorageManager.newPage`). */
    fun initData(){
        data.putLong(PageHeaderOffset.PAGE_ID.offset, pageId)
        data.put(PageHeaderOffset.PAGE_TYPE.offset, PageType.EMPTY.value)
        data.put(PageHeaderOffset.RESERVED_ONE.offset, 0)
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, 0)
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, HEADER_SIZE.toShort())
        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (indexConfig.pageSize-1).toShort())
        data.putShort(PageHeaderOffset.RESERVED_THREE.offset, 0)
        data.putShort(PageHeaderOffset.RESERVED_TWO.offset, 0)
        data.putLong(PageHeaderOffset.PARENT_PAGE_ID.offset, 0)
        data.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, INVALID_PAGE_ID)
        data.putLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset, INVALID_PAGE_ID)
        data.putLong(PageHeaderOffset.LSN.offset, 0)
    }

    var type: PageType
        get() = PageType.fromValue(data[PageHeaderOffset.PAGE_TYPE.offset]) ?: throw IllegalStateException("Page type should be set")
        set(value) {
            data.put(PageHeaderOffset.PAGE_TYPE.offset, value.value)
        }

    /** Offset where the record data area ends (data grows back-to-front, so this shrinks as records are added). */
    val freeSpaceEnd: Int
        get() = data.getShort(PageHeaderOffset.FREE_SPACE_END.offset).toInt()

    /** Offset where the slot array ends (grows front-to-back as records are added). */
    val freeSpaceStart: Int
        get() = data.getShort(PageHeaderOffset.FREE_SPACE_START.offset).toInt()

    /** Bytes still available between the slot array and the data area — see [SlottedPage]'s layout diagram. */
    val freeSpace: Int
        get() = freeSpaceEnd - freeSpaceStart + 1

    /** Number of live records (slots) currently on this page. */
    val recordCount: Int
        get() = data.getShort(PageHeaderOffset.RECORD_COUNT.offset).toInt()

    /**
     * `LEFT_SIBLING_PAGE_ID`'s header slot, reused for a third meaning: an internal node has no
     * left sibling in this design (only leaves are sibling-linked, for range scans), so an
     * internal node instead stores its leftmost child pointer here. Same underlying bytes as
     * [leftSiblingPageId] — callers must use whichever accessor matches the page's actual type.
     * */
    var leftMostChildPageId: Long
        get() = data.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)
        set(leftMostPageId) {
            data.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, leftMostPageId)
        }

    /**
     * Leaf: the left sibling leaf's page id (or [INVALID_PAGE_ID] if this is the leftmost leaf) —
     * used by [index.btree.Cursor] for a BACKWARD scan / walking left. Internal: unused as a
     * "sibling" at all;
     * see [leftMostChildPageId] for what this field means there instead. Once a page is freed,
     * [storageEngine.FreeSpaceManager] repurposes this same field a third time, as the free list's
     * "next free page" pointer.
     * */
    var leftSiblingPageId: Long
        get() = data.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)
        set(siblingPageId) {
            data.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, siblingPageId)
        }

    var rightSiblingPageId: Long
        get() = data.getLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset)
        set(siblingPageId) {
            data.putLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset, siblingPageId)
        }

    /**
     * Bumps [recordCount] by one and advances [freeSpaceStart] by one slot's worth of space
     * (called by [SlottedPage.insertData] after writing a new slot).
     * */
    internal fun increaseRecordCount(){
        val recordCount = data.getShort(PageHeaderOffset.RECORD_COUNT.offset)
        val freeSpaceStartIdx = freeSpaceStart
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, (recordCount + 1).toShort())
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, (freeSpaceStartIdx + SLOT_SIZE).toShort())
    }

    /** Mirror of [increaseRecordCount] for a removed slot. */
    internal fun decreaseRecordCount(){
        val recordCount = data.getShort(PageHeaderOffset.RECORD_COUNT.offset)
        val freeSpaceStartIdx = freeSpaceStart
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, (recordCount - 1).toShort())
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, (freeSpaceStartIdx - SLOT_SIZE).toShort())
    }

    companion object{
        internal const val HEADER_SIZE = 56
        internal const val SLOT_SIZE: Short = 4
    }
}
