package storageEngine

import catalog.MetaPageData
import storageEngine.exception.StorageEngineException
import util.INVALID_PAGE_ID
import util.LockMode
import util.META_PAGE_ID
import util.MetaPageOffset
import util.START_PAGE_ID
import util.requireOrThrow

class MetaPageManager(
    private val bufferPoolManager: BufferPoolManager,
) {

    fun initialize(): MetaPageData {
        return if (bufferPoolManager.getNumPages() == 0L) createMetaPage() else loadMetaPage()
    }

    fun updateRootPageId(metaPageOffset: MetaPageOffset, newRootPageId: Long){
        requireOrThrow(
            metaPageOffset in setOf(
                MetaPageOffset.INDEX_CATALOG_ROOT_PAGE_ID,
                MetaPageOffset.TABLE_CATALOG_ROOT_PAGE_ID,
                MetaPageOffset.COLUMN_CATALOG_ROOT_PAGE_ID
            )
        ){
            StorageEngineException.InvalidMetaArgument(metaPageOffset)
        }
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.WRITE)
        pageLock.asWriteView { buffer ->
            buffer.putLong(metaPageOffset.offset, newRootPageId)
        }
        pageLock.close()
    }

    fun getNextId(metaPageOffset: MetaPageOffset): Long{
        requireOrThrow(metaPageOffset in setOf(MetaPageOffset.NEXT_INDEX_ID, MetaPageOffset.NEXT_TABLE_ID)){
            StorageEngineException.InvalidMetaArgument(metaPageOffset)
        }
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.WRITE)
        var tableId = -1L
        pageLock.asWriteView { buffer ->
            tableId = buffer.getLong(metaPageOffset.offset)
            buffer.putLong(metaPageOffset.offset, tableId + 1)
        }
        pageLock.close()
        return tableId
    }

    private fun createMetaPage(): MetaPageData{
        val pageLock = bufferPoolManager.newPage(META_PAGE_ID)
        pageLock.asWriteView { buffer ->
            buffer.putLong(MetaPageOffset.FREE_LIST_HEAD_PAGE_ID.offset, INVALID_PAGE_ID)
            buffer.putLong(MetaPageOffset.NEXT_PAGE_ID.offset, START_PAGE_ID)
            buffer.putLong(MetaPageOffset.TABLE_CATALOG_ROOT_PAGE_ID.offset, INVALID_PAGE_ID)
            buffer.putLong(MetaPageOffset.COLUMN_CATALOG_ROOT_PAGE_ID.offset, INVALID_PAGE_ID)
            buffer.putLong(MetaPageOffset.INDEX_CATALOG_ROOT_PAGE_ID.offset, INVALID_PAGE_ID)
            buffer.putLong(MetaPageOffset.NEXT_TABLE_ID.offset, 1L)
            buffer.putLong(MetaPageOffset.NEXT_INDEX_ID.offset, 1L)
        }
        pageLock.close()
        return MetaPageData(
            INVALID_PAGE_ID,
            INVALID_PAGE_ID,
            INVALID_PAGE_ID
        )
    }

    private fun loadMetaPage(): MetaPageData{
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.READ)
        val metaData = pageLock.asReadView { buffer ->
            MetaPageData(
                buffer.getLong(MetaPageOffset.TABLE_CATALOG_ROOT_PAGE_ID.offset),
                buffer.getLong(MetaPageOffset.COLUMN_CATALOG_ROOT_PAGE_ID.offset),
            buffer.getLong(MetaPageOffset.INDEX_CATALOG_ROOT_PAGE_ID.offset)
            )

        }
        pageLock.close()
        return metaData
    }
}