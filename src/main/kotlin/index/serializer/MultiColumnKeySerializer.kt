package index.serializer

import index.util.*
import java.lang.IndexOutOfBoundsException


class MultiColumnKeySerializer(schema: KeySchema): BaseKeySerializer<List<Any?>>(schema) {
    override fun serialize(key: List<Any?>): ByteArray {
        require(key.size <= schema.columns.size) { "Too many key values for schema" }
        return buildList{
            for (idx in key.indices){
                val packed = packKeyItem(key[idx], schema.columns[idx])
                addAll(packed.toList())
            }
        }.toByteArray()
    }
    override fun deserialize(bytes: ByteArray): List<Any?> {
        val unpackedKeys = mutableListOf<Any?>()
        var offset = 0

        for (column in schema.columns){
            val (value, consumed) = try {
                unpackKeyItem(bytes, offset, column)
            } catch (_: IndexOutOfBoundsException){
                break
            }
            unpackedKeys.add(value)
            offset += consumed
        }
        return unpackedKeys
    }

    override fun format(key: List<Any?>): String {
        val viewBuilder = StringBuilder()
        for(keyItem in key){
            viewBuilder.append("$keyItem|")
        }
        viewBuilder.append(" ")
        return viewBuilder.toString()
    }
}