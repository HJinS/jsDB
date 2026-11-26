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
class MultiColumnKeySerializer(schema: IndexKeySchema) : BaseKeySerializer<List<Any?>>(schema) {
    /**
     * Packs each column of [key] with [packKeyItem], in schema order, concatenated so the result
     * is byte-comparable exactly like the full key (each column's own encoding is already
     * byte-comparable and ASC/DESC-aware; concatenation preserves column-priority ordering).
     *
     * [key] may have **fewer** columns than the schema: a prefix, used only as a search bound (keys
     * written to an index always carry every column, see `IndexHandle.extractKey`). Nothing is added
     * for the missing columns. A byte sequence sorts before every longer one that starts with it, and
     * each column's encoding ends in a terminator, so a prefix key already sorts at or before every
     * full key sharing it and after every key of an earlier group - and, being shorter, is never
     * byte-equal to any stored key.
     *
     * (It used to be padded with the missing column's NULL byte, `0xFF` for DESC, which sorts after
     * every non-NULL DESC value - #76.)
     * */
    override fun serialize(key: List<Any?>): ByteArray {
        require(key.size <= schema.indexColumns.size) { "Too many key values for schema" }
        var totalByteSize = 0
        val tempArray = ArrayList<ByteArray>(schema.indexColumns.size)
        for (idx in key.indices) {
            val packed = packKeyItem(key[idx], schema.indexColumns[idx])
            tempArray.add(packed)
            totalByteSize += packed.size
        }

        val resultArray = ByteArray(totalByteSize)
        var offset = 0
        for (byteArray in tempArray) {
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

    /**
     * Unpacks [bytes] column by column via [unpackKeyItem], stopping when the bytes run out, so a
     * prefix key from [serialize] comes back as exactly its prefix values (`[1]` stays `[1]`, not
     * `[1, null]`). A [serializeUpper] result is comparison-only and isn't meant to be decoded.
     * */
    override fun deserialize(bytes: ByteArray): List<Any?> {
        val unpackedKeys = mutableListOf<Any?>()
        var offset = 0

        for (column in schema.indexColumns) {
            if (offset >= bytes.size) break
            val (value, consumed) = unpackKeyItem(bytes, offset, column)
            unpackedKeys.add(value)
            offset += consumed
        }
        return unpackedKeys
    }

    /** Debug-only pipe-joined rendering of [key]'s column values (e.g. for `BTree.printTree`). */
    override fun format(key: List<Any?>): String {
        val viewBuilder = StringBuilder()
        for (keyItem in key) {
            viewBuilder.append("$keyItem|")
        }
        viewBuilder.append(" ")
        return viewBuilder.toString()
    }

}
