package index.exception

sealed class SerializerException(message: String?, cause: Throwable?): IndexException(message, cause) {
    class InvalidBytesException(cause: Throwable?=null): 
        SerializerException("Invalid bytes for serialization/deserialization.", cause)

    sealed class DecodeException(message: String, cause: Throwable?): SerializerException(message, cause) {
        class PositionOutOfBoundsException(offset: Int, size: Int, cause: Throwable?=null): 
            DecodeException("Position $offset should be less than total byte size $size.", cause)
            
        class VarIntTooLongException(cause: Throwable?=null): 
            DecodeException("VarInt is too long", cause)
            
        class InvalidUUIDLengthException(actualLength: Int, cause: Throwable?=null): 
            DecodeException("UUID should be 16 bytes, but got $actualLength", cause)
    }
}
