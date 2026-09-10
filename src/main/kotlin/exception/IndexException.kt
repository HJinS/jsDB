package exception

import util.EngineErrorDetail
import util.SqlState

sealed class IndexException(
    sqlState: SqlState,
    detail: EngineErrorDetail,
    cause: Throwable? = null
): RuntimeException(detail.toMessage(sqlState), cause) {
    class InvalidTraceStack(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class EmptyTree(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class InvalidNodeType(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class InvalidSafeCheck(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class InvalidBytes(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class PositionOutOfBounds(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class VarIntTooLong(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class InvalidUUIDLength(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class InvalidTraceObject(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)

    class LeafNodeNotFound(detail: EngineErrorDetail, cause: Throwable? = null):
        IndexException(SqlState.INTERNAL_ERROR, detail, cause)
}
