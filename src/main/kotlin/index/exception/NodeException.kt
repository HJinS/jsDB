package index.exception

sealed class NodeException(message: String?, cause: Throwable?): IndexException(message, cause){

}
