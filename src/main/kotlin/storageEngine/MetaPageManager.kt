package storageEngine

import schema.MetaPageData
import exception.StorageEngineException
import util.EngineErrorDetail
import util.INVALID_PAGE_ID
import util.LockMode
import util.META_PAGE_ID
import util.MetaPageOffset
import util.START_PAGE_ID
import util.requireOrThrow

/**
 * Owns the fixed meta page ([util.META_PAGE_ID], page 0): the system catalog's three root page
 * ids, the next-file-growth counter, the free list head, and the next table/index id counters
 * (see [util.MetaPageOffset] for the exact byte layout). This is what makes the catalog (and
 * hence every table/index) findable again after a re-open — see [index.btree.BTree]'s
 * `onRootChanged`.
 * */
class MetaPageManager(
    private val bufferPoolManager: BufferPoolManager,
) {

    /** Bootstraps the meta page on a brand-new (empty) file, or loads the existing one otherwise. */
    fun initialize(): MetaPageData {
        return if (bufferPoolManager.getNumPages() == 0L) createMetaPage() else loadMetaPage()
    }

    /**
     * Persists a catalog tree's new root page id — the `onRootChanged` callback wiring in
     * `DataBase.initialize` calls this whenever one of the three catalog [index.btree.BTree]s'
     * root changes.
     *
     * @param metaPageOffset Must be one of the three `*_CATALOG_ROOT_PAGE_ID` offsets.
     * */
    fun updateRootPageId(metaPageOffset: MetaPageOffset, newRootPageId: Long){
        requireOrThrow(
            metaPageOffset in setOf(
                MetaPageOffset.INDEX_CATALOG_ROOT_PAGE_ID,
                MetaPageOffset.TABLE_CATALOG_ROOT_PAGE_ID,
                MetaPageOffset.COLUMN_CATALOG_ROOT_PAGE_ID
            )
        ){
            StorageEngineException.InvalidMetaArgument(EngineErrorDetail(reason = "Invalid argument: $metaPageOffset"))
        }
        val pageLock = bufferPoolManager.fetchPage(META_PAGE_ID, LockMode.WRITE)
        pageLock.asWriteView { buffer ->
            buffer.putLong(metaPageOffset.offset, newRootPageId)
        }
        pageLock.close()
    }

    /**
     * Returns the next unused id for [metaPageOffset] and increments the stored counter — a
     * simple monotonic sequence, not reclaimed on delete (unlike page ids, see [FreeSpaceManager]).
     *
     * @param metaPageOffset Must be [MetaPageOffset.NEXT_TABLE_ID] or [MetaPageOffset.NEXT_INDEX_ID].
     * */
    fun getNextId(metaPageOffset: MetaPageOffset): Long{
        requireOrThrow(metaPageOffset in setOf(MetaPageOffset.NEXT_INDEX_ID, MetaPageOffset.NEXT_TABLE_ID)){
            StorageEngineException.InvalidMetaArgument(EngineErrorDetail(reason = "Invalid argument: $metaPageOffset"))
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

    /** Initializes a fresh meta page: no catalog trees yet, empty free list, id counters starting at 1. */
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

    /** Reads the three catalog root page ids back from an existing meta page. */
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