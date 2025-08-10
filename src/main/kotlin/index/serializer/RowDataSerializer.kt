package index.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass


class RowDataSerializer<V: Any>(
    kClass: KClass<V>
) : ValueSerializer<V> {

    @Suppress("UNCHECKED_CAST")
    private val kSerializer = serializer(kClass.java) as KSerializer<V>

    override fun serialize(value: V): ByteArray {
        return Json.encodeToString(kSerializer, value).toByteArray()
    }

    override fun deserialize(bytes: ByteArray): V {
        return Json.decodeFromString(kSerializer, bytes.toString(Charsets.UTF_8))
    }

}