package helper.serializer

import index.serializer.ValueSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class RowDataSerializerHelper<V: Any>(
    private val kSerializer: KSerializer<V>,
) : ValueSerializer<V> {

    override fun serialize(value: V): ByteArray {
        return Json.encodeToString(kSerializer, value).toByteArray()
    }

    override fun deserialize(bytes: ByteArray): V {
        return Json.decodeFromString(kSerializer, bytes.toString(Charsets.UTF_8))
    }

}