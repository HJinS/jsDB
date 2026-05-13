package index.exception

sealed class IndexException(message: String?, cause: Throwable?): RuntimeException(message, cause){
    class InvalidTraceStackException(
        name: String, targetTable: String, cause: Throwable?=null
    ): IndexException("Unexpected node trace data invalid. IndexName: $name TargetTableName: $targetTable", cause)

    class EmptyTreeException(
        name: String, targetTable: String, cause: Throwable?=null
    ): IndexException("Search function should be called when the tree is not empty. IndexName: $name TargetTableName: $targetTable", cause)
}
