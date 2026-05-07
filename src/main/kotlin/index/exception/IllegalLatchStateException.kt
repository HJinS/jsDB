package index.exception

import storageEngine.page.PageLock


sealed class IllegalLatchStateException(message: String?, cause: Throwable?): IndexException(message, cause){
    class InvalidLockObjectError(frameId: Int, cause: Throwable?=null): 
        IllegalLatchStateException("The provided lock object does not match the most recently pushed lock in the latch queue.: $frameId", cause)

    class InvalidTraceObjectError(pageId: Long, cause: Throwable?=null): 
        IllegalLatchStateException("The provided trace object does not match the most recently pushed lock in the latch queue.: $pageId", cause)
}
