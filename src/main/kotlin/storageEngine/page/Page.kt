package storageEngine.page

import config.PageConfig
import mu.KotlinLogging
import storageEngine.util.PageHeaderOffset
import storageEngine.util.PageType
import java.nio.ByteBuffer


open class Page(
    val pageConfig: PageConfig,
    internal val data: ByteBuffer,
    internal val pageId: Long = -1,
    internal val pageType: PageType = PageType.EMPTY,
){
    internal val logger = KotlinLogging.logger {}

    fun initData(){
        data.putLong(PageHeaderOffset.PAGE_ID.offset, pageId)
        data.put(PageHeaderOffset.PAGE_TYPE.offset, pageType.value)
        data.put(PageHeaderOffset.RESERVED_ONE.offset, 0)
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, 0)
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, HEADER_SIZE.toShort())
        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (pageConfig.pageSize-1).toShort())
        data.putShort(PageHeaderOffset.FREE_SLOT_HEAD.offset, (-1).toShort())
        data.putShort(PageHeaderOffset.RESERVED_TWO.offset, 0)
        data.putLong(PageHeaderOffset.PARENT_PAGE_ID.offset, 0)
        data.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, 0)
        data.putLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset, 0)
        data.putLong(PageHeaderOffset.LSN.offset, 0)
    }

    val type: PageType
        get() = PageType.fromValue(data[PageHeaderOffset.PAGE_TYPE.offset]) ?: throw IllegalStateException("Page type should be set")

    val freeSpaceEnd: Int
        get() = data.getShort(PageHeaderOffset.FREE_SPACE_END.offset).toInt()

    val freeSpaceStart: Int
        get() = data.getShort(PageHeaderOffset.FREE_SPACE_START.offset).toInt()

    val recordCount: Int
        get() = data.getShort(PageHeaderOffset.RECORD_COUNT.offset).toInt()

    val leftSiblingPageId: Long
        get() = data.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)

    val rightSiblingPageId: Long
        get() = data.getLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset)

    internal fun increaseRecordCount(){
        val recordCount = data.getShort(PageHeaderOffset.RECORD_COUNT.offset)
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, (recordCount + 1).toShort())
    }

    internal fun decreaseRecordCount(){
        val recordCount = data.getShort(PageHeaderOffset.RECORD_COUNT.offset)
        data.putShort(PageHeaderOffset.RECORD_COUNT.offset, (recordCount - 1).toShort())
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