package index.serializer

import java.nio.ByteBuffer


class PageIDSerializer {
    fun serialize(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()

    fun deserialize(bytes: ByteArray): Long = ByteBuffer.wrap(bytes).long

}