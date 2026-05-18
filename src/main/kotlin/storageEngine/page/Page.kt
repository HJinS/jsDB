package storageEngine.page

import config.IndexConfig
import mu.KotlinLogging
import storageEngine.util.PageHeaderOffset
import storageEngine.util.PageType
import java.nio.ByteBuffer


open class Page(
    val indexConfig: IndexConfig,
    internal val data: ByteBuffer,
    internal val pageId: Long = -1
){
    internal val logger = KotlinLogging.logger {}

    fun initData(){
        data.putLong(PageHeaderOffset.PAGE_ID.offset, pageId)
        data.put(PageHeaderOffset.PAGE_TYPE.offset, PageType.EMPTY.value)
        data.put(PageHeaderOffset.RESERVED_ONE.offset, 0)
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, 0)
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, HEADER_SIZE.toShort())
        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (indexConfig.pageSize-1).toShort())
        data.putShort(PageHeaderOffset.FREE_SLOT_HEAD.offset, (-1).toShort())
        data.putShort(PageHeaderOffset.RESERVED_TWO.offset, 0)
        data.putLong(PageHeaderOffset.PARENT_PAGE_ID.offset, 0)
        data.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, -1)
        data.putLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset, -1)
        data.putLong(PageHeaderOffset.LSN.offset, 0)
    }

    var type: PageType
        get() = PageType.fromValue(data[PageHeaderOffset.PAGE_TYPE.offset]) ?: throw IllegalStateException("Page type should be set")
        set(value) {
            data.put(PageHeaderOffset.PAGE_TYPE.offset, value.value)
        }

    val freeSpaceEnd: Int
        get() = data.getShort(PageHeaderOffset.FREE_SPACE_END.offset).toInt()

    val freeSpaceStart: Int
        get() = data.getShort(PageHeaderOffset.FREE_SPACE_START.offset).toInt()

    val recordCount: Int
        get() = data.getShort(PageHeaderOffset.RECORD_COUNT.offset).toInt()

    /**
     * internal node일 경우에는 leftSiblingPageId를 leftMostChildPageId로 사용
     * */
    var leftMostChildPageId: Long
        get() = data.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)
        set(leftMostPageId) {
            data.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, leftMostPageId)
        }

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

    internal fun increaseRecordCount(){
        val recordCount = data.getShort(PageHeaderOffset.RECORD_COUNT.offset)
        val freeSpaceStartIdx = freeSpaceStart
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, (recordCount + 1).toShort())
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, (freeSpaceStartIdx + SLOT_SIZE).toShort())
    }

    internal fun decreaseRecordCount(){
        val recordCount = data.getShort(PageHeaderOffset.RECORD_COUNT.offset)
        val freeSpaceStartIdx = freeSpaceStart
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, (recordCount - 1).toShort())
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, (freeSpaceStartIdx - SLOT_SIZE).toShort())
    }

    internal fun getSlotId(slotArrayOffset: Int): Int{
        val slotArrayStartBytes = HEADER_SIZE
        return (slotArrayOffset - slotArrayStartBytes) / SLOT_SIZE
    }

    companion object{
        internal const val HEADER_SIZE = 56
        internal const val SLOT_SIZE: Short = 4
    }
}