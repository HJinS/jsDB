package storageEngine

import storageEngine.util.INVALID_PAGE_ID
import storageEngine.util.META_PAGE_ID
import storageEngine.util.MetaPageOffset
import storageEngine.util.START_PAGE_ID

class DatabaseInitializer(
    private val bufferPoolManager: BufferPoolManager
) {
    fun initMetaPage(){
        val pageLock = bufferPoolManager.newPage(META_PAGE_ID)
        pageLock.asWriteView { buffer ->
            buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, INVALID_PAGE_ID)
            buffer.putLong(MetaPageOffset.NEXT_PAGE_ID.offset, START_PAGE_ID)
            buffer.putLong(MetaPageOffset.TABLE_CATALOG_ROOT_PAGE_ID.offset, INVALID_PAGE_ID)
            buffer.putLong(MetaPageOffset.COLUMN_CATALOG_ROOT_PAGE_ID.offset, INVALID_PAGE_ID)
            buffer.putLong(MetaPageOffset.INDEX_CATALOG_ROOT_PAGE_ID.offset, INVALID_PAGE_ID)
        }
        pageLock.close()
    }
}