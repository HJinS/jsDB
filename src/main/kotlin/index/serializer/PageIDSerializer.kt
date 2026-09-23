package index.serializer

import java.nio.ByteBuffer


/**
 * Fixed 8-byte encoding for a raw page id — used as [index.btree.node.InternalNode]'s child
 * pointer values (not a row/key value, so this doesn't implement [ValueSerializer]).
 * */
class PageIDSerializer {
    fun serialize(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()

    fun deserialize(bytes: ByteArray): Long = ByteBuffer.wrap(bytes).long

}