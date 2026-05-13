package index.exception

import storageEngine.util.PageType

sealed class NodeException(message: String?, cause: Throwable?): IndexException(message, cause) {
    class InvalidNodeTypeException(
        type: PageType, cause: Throwable?=null
    ): NodeException("Invalid node type. type: $type", cause)
}
