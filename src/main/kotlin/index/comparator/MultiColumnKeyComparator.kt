package index.comparator

import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import index.util.compareTo
import index.util.decodeVarInt


class MultiColumnKeyComparator(private val schema: KeySchema): KeyComparator {
    override fun compare(key1: ByteArray, key2: ByteArray): Int {
        var offset1 = 0
        var offset2 = 0
        for (column in schema.columns){
            val byte1 = key1[offset1]
            val byte2 = key2.getOrElse(offset2) { _ -> 0x00.toByte()}

            val isNull1 = byte1 == 0x00.toByte()
            val isNull2 = byte2 == 0x00.toByte()
            if (isNull1 && isNull2){
                offset1++
                offset2++
                continue
            }
            if (isNull1) return if (column.descending) 1 else -1
            if (isNull2) return if (column.descending) -1 else 1
            offset1++
            offset2++

            val (cmp, consumed1, consumed2) = comparePackedItem(key1, offset1, key2, offset2, column)
            if (cmp != 0) return cmp
            offset1 += consumed1
            offset2 += consumed2
        }
        return 0
    }

    private fun comparePackedItem(bytes1: ByteArray, offset1: Int, bytes2: ByteArray, offset2: Int, column: Column): Triple<Int, Int, Int>{
        fun extractBytes(bytes: ByteArray, offset: Int, length: Int): ByteArray{
            return bytes.copyOfRange(offset, offset + length)
        }

        return when (column.type){
            ColumnType.INT, ColumnType.FLOAT -> {
                val extractedBytes1 = extractBytes(bytes1, offset1, 4)
                val extractedBytes2 = extractBytes(bytes2, offset2, 4)
                Triple(extractedBytes1.compareTo(extractedBytes2, column.descending), 4, 4)
            }
            ColumnType.STRING, ColumnType.BYTES -> {
                val (len1, lenBytes1) = decodeVarInt(bytes1, offset1)
                val (len2, lenBytes2) = decodeVarInt(bytes2, offset2)
                val extractedBytes1 = extractBytes(bytes1, offset1 + lenBytes1, len1)
                val extractedBytes2 = extractBytes(bytes2, offset2 + lenBytes2, len2)
                Triple(extractedBytes1.compareTo(extractedBytes2, descending=column.descending), lenBytes1 + len1, lenBytes2 + len2)
            }
            ColumnType.LONG, ColumnType.DOUBLE, ColumnType.LOCAL_DATE_TIME, ColumnType.INSTANT, ColumnType.LOCAL_DATE -> {
                val extractedBytes1 = extractBytes(bytes1, offset1, 8)
                val extractedBytes2 = extractBytes(bytes2, offset2, 8)
                Triple(extractedBytes1.compareTo(extractedBytes2, column.descending), 8, 8)
            }
            ColumnType.SHORT -> {
                val extractedBytes1 = extractBytes(bytes1, offset1, 2)
                val extractedBytes2 = extractBytes(bytes2, offset2, 2)
                Triple(extractedBytes1.compareTo(extractedBytes2, column.descending), 2, 2)
            }
            ColumnType.BOOLEAN, ColumnType.BYTE -> {
                val extractedBytes1 = extractBytes(bytes1, offset1, 1)
                val extractedBytes2 = extractBytes(bytes2, offset2, 1)
                Triple(extractedBytes1.compareTo(extractedBytes2, column.descending), 1, 1)
            }
            ColumnType.UUID -> {
                val len = 16
                val extractedBytes1 = extractBytes(bytes1, offset1, len)
                val extractedBytes2 = extractBytes(bytes2, offset2, len)
                Triple(extractedBytes1.compareTo(extractedBytes2, column.descending), len, len)
            }
        }
    }
}