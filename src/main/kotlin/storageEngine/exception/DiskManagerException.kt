package storageEngine.exception

sealed class DiskManagerException(message: String?, cause: Throwable?): StorageEngineException(message, cause) {
    class InvalidReadOffsetException(
        pageId: Long, cause: Throwable? = null
    ): DiskManagerException("PageId: $pageId not exist", cause)

    class FileCorruptedException(
        fileSize: Long, pageSize: Int, cause: Throwable? = null
    ): DiskManagerException("File size $fileSize is not a multiple of pageSize $pageSize.", cause)
}