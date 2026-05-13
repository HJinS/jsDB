package index.exception

sealed class BTreeException(message: String?, cause: Throwable?): IndexException(message, cause) {
    class UnexpectedInternalError(cause: Throwable?=null): 
        BTreeException("An unexpected internal consistency error occurred in BTree logic.", cause)

    class DuplicateKeyException(key: Any, cause: Throwable?=null): 
        BTreeException("Duplicate key found: $key", cause)

    class LeafNodeNotFoundException(key: Any?, cause: Throwable?=null): 
        BTreeException("Could not find leaf node for key: $key", cause)
}
