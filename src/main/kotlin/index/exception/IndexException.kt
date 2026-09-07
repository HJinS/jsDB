package index.exception

import util.PageType

sealed class IndexException(message: String?, cause: Throwable? = null): RuntimeException(message, cause) {
    class InvalidTraceStackException(
        name: String, targetTable: String, cause: Throwable? = null
    ): IndexException("Unexpected node trace data invalid. IndexName: $name TargetTableName: $targetTable", cause)

    class EmptyTreeException(
        name: String, targetTable: String, cause: Throwable? = null
    ): IndexException("Search function should be called when the tree is not empty. IndexName: $name TargetTableName: $targetTable", cause)

    // Node
    class InvalidNodeTypeException(
        type: PageType, cause: Throwable? = null
    ): IndexException("Invalid node type. type: $type", cause)

    class InvalidSafeCheckException(
        cause: Throwable? = null
    ): IndexException("Key, Value must be provided for safe check when optMode is Insert or Update", cause)

    // Serializer
    class InvalidBytesException(
        cause: Throwable? = null
    ): IndexException("Invalid bytes for serialization/deserialization.", cause)

    class PositionOutOfBoundsException(
        offset: Int, size: Int, cause: Throwable? = null
    ): IndexException("Position $offset should be less than total byte size $size.", cause)

    class VarIntTooLongException(
        cause: Throwable? = null
    ): IndexException("VarInt is too long", cause)

    class InvalidUUIDLengthException(
        actualLength: Int, cause: Throwable? = null
    ): IndexException("UUID should be 16 bytes, but got $actualLength", cause)

    class InvalidTraceObjectError(
        pageId: Long, cause: Throwable? = null
    ): IndexException("The provided trace object does not match the most recently pushed lock in the latch queue.: $pageId", cause)

    class LeafNodeNotFoundException(
        key: Any?, cause: Throwable? = null
    ): IndexException("Could not find leaf node for key: $key", cause)
}
