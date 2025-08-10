package index.serializer

interface KeySerializer<K> {
    fun serialize(key: K): ByteArray
    fun deserialize(bytes: ByteArray): K
    fun format(key: K): String
}

interface ValueSerializer<V> {
    fun serialize(value: V): ByteArray
    fun deserialize(bytes: ByteArray): V
}