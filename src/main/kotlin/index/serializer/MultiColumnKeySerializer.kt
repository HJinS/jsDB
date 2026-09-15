package index.serializer

import schema.*
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


    /**
     * Serialize [key] as an exclusive upper bound for every full key sharing [key] as a prefix.
     *
     * Every column encoding (regardless of type or asc/desc) ends with a terminator produced by
     * [util.escapeZeroBytes], so the successor of a prefix is computed generically: walk the packed
     * prefix bytes from the right, increment the first byte that isn't already 0xFF, and drop
     * everything after it. Because the leading flag byte of the last column is never 0xFF, the
     * carry always resolves within that column's own region and never reaches into an earlier column.
     */
    override fun serializeUpper(key: List<Any?>): ByteArray? {
        require(key.isNotEmpty()) { "At least one key value is required to compute an upper bound" }
        require(key.size <= schema.indexColumns.size) { "Too many key values for schema" }

        var totalByteSize = 0
        val tempArray = ArrayList<ByteArray>(key.size)
        for (idx in key.indices) {
            val packed = packKeyItem(key[idx], schema.indexColumns[idx])
            tempArray.add(packed)
            totalByteSize += packed.size
        }

        val prefixBytes = ByteArray(totalByteSize)
        var offset = 0
        for (byteArray in tempArray) {
            System.arraycopy(byteArray, 0, prefixBytes, offset, byteArray.size)
            offset += byteArray.size
        }

        for (idx in prefixBytes.indices.reversed()) {
            // Change byte data to unsigned
            val unsigned = prefixBytes[idx].toInt() and 0xFF
            if (unsigned < 0xFF) {
                prefixBytes[idx] = (unsigned + 1).toByte()
                return prefixBytes.copyOfRange(0, idx + 1)
            }
        }
        return null
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
        for (keyItem in key) {
            viewBuilder.append("$keyItem|")
        }
        viewBuilder.append(" ")
        return viewBuilder.toString()
    }
}
}
