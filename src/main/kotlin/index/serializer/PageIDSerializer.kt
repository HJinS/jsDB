package index.serializer

import java.nio.ByteBuffer


class PageIDSerializer: ValueSerializer<Long> {
    override fun serialize(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()

    override fun deserialize(bytes: ByteArray): Long = ByteBuffer.wrap(bytes).long

}