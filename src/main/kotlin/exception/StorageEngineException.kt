package exception

import util.EngineErrorDetail
import util.SqlState

/** Failures from the storage stack below indexing ([storageEngine.DiskManager]/[storageEngine.BufferPoolManager]/[storageEngine.page.SlottedPage]) — always [SqlState.INTERNAL_ERROR], since these are disk/memory-level invariant violations, not query-triggerable. */
sealed class StorageEngineException(
    detail: EngineErrorDetail,
    cause: Throwable? = null
): RuntimeException(detail.toMessage(SqlState.INTERNAL_ERROR), cause) {
    class InvalidReadOffset(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class FileCorrupted(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class InvalidPageId(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class InvalidPageType(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class LRUEvict(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class PageNotFoundInCache(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class PageInUse(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class UnExpected(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class SlotOutOfBound(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class SlotShift(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class PageFull(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)

    class InvalidMetaArgument(detail: EngineErrorDetail, cause: Throwable? = null):
        StorageEngineException(detail, cause)
}
