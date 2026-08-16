package index.serializer

import index.util.*
import java.lang.IndexOutOfBoundsException

/**
 * Serializer to serialize multi-column keys.
 *
 * @property schema Schema of the keys.
 * @see BaseKeySerializer
 * @see IndexKeySchema
 * */
class MultiColumnKeySerializer(schema: IndexKeySchema): BaseKeySerializer<List<Any?>>(schema) {


    override fun serialize(key: List<Any?>): ByteArray {
        require(key.size <= schema.indexColumns.size) { "Too many key values for schema" }
        var totalByteSize = 0
        val tempArray = ArrayList<ByteArray>(schema.indexColumns.size)
        for(idx in schema.indexColumns.indices){
            if(idx >= key.size) {
                val padding = if(schema.indexColumns[idx].descending) byteArrayOf(0xFF.toByte()) else byteArrayOf(0x00.toByte())
                tempArray.add(padding)
                totalByteSize += 1
                break
            } else{
                val packed = packKeyItem(key[idx], schema.indexColumns[idx])
                tempArray.add(packed)
                totalByteSize += packed.size
            }
        }

        val resultArray = ByteArray(totalByteSize)
        var offset = 0
        for(byteArray in tempArray){
            System.arraycopy(byteArray, 0, resultArray, offset, byteArray.size)
            offset += byteArray.size
        }
        return resultArray
    }
    override fun deserialize(bytes: ByteArray): List<Any?> {
        val unpackedKeys = mutableListOf<Any?>()
        var offset = 0

        for (column in schema.indexColumns){
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