package index.exception

open class SerializerException(message: String?, cause: Throwable?): BTreeException(message, cause)