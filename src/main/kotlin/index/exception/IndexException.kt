package index.exception

import storageEngine.util.PageType


open class IndexException(message: String?, cause: Throwable?): RuntimeException(message, cause){
    class InvalidTraceStackException(
        name: String, targetTable: String, cause: Throwable?
    ): IndexException("Unexpected node trace data invalid. IndexName: $name TargetTableName: $targetTable", cause)

    class EmptyTreeException(
        name: String, targetTable: String, cause: Throwable?
    ): IndexException("Search function should be called when the tree is not empty. IndexName: $name TargetTableName: $targetTable", cause)

    class InvalidNodeTypeException(
        type: PageType, cause: Throwable?
    ): IndexException("Invalid node type. type: $type", cause)
}