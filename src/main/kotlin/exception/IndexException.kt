package exception

import util.EngineErrorDetail
import util.ErrorCode

/**
 * Failures from [index.btree.BTree]/[index.serializer.*] - every case here is a structural/logic
 * invariant violation (corrupt trace stack, invalid bytes, wrong node type), never something a
 * caller's query could trigger through normal use. Which failure it is, is told by [code].
 * */
class IndexException(
    override val code: ErrorCode,
    override val detail: EngineErrorDetail,
    cause: Throwable? = null,
) : RuntimeException(detail.toMessage(code), cause), CodedException
