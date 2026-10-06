package exception

import util.EngineErrorDetail
import util.ErrorCode

/** Failures from the storage stack below indexing ([storageEngine.DiskManager]/[storageEngine.BufferPoolManager]/[storageEngine.page.SlottedPage]) - disk/memory-level invariant violations, not query-triggerable. Which failure it is, is told by [code]. */
class StorageEngineException(
    override val code: ErrorCode,
    override val detail: EngineErrorDetail,
    cause: Throwable? = null,
) : RuntimeException(detail.toMessage(code), cause), CodedException
