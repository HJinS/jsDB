package index.util


fun List<Any?>.compareUnpackedKey(otherKey: List<Any?>, schema: KeySchema): Int{
    for(idx in schema.columns.indices){
        val column = schema.columns[idx]
        val value1 = this[idx]
        val value2 = otherKey[idx]

        val comparison = when{
            value1 == null && value2 == null -> 0
            value1 == null -> -1
            value2 == null -> 1
            else -> {
                when (column.type){
                    ColumnType.STRING -> {
                        val string1 = value1 as String
                        val string2 = value2 as String
                        column.collation?.compare(string1, string2) ?: string1.compareTo(string2)
                    }
                    ColumnType.BYTES -> {
                        val byte1 = value1 as ByteArray
                        val byte2 = value2 as ByteArray
                        byte1.zip(byte2).map { it.first.compareTo(it.second) }.firstOrNull { it != 0 } ?: (byte1.size-byte2.size)
                    }
                    else -> (value1 as Comparable<Any>).compareTo(value2 as  Comparable<Any>)
                }
            }
        }
        if (comparison != 0) return if (column.descending) -comparison else comparison
    }
    return 0
}

// 사전식 정렬을 위한 바이트 배열 비교 (unsigned)
operator fun ByteArray.compareTo(other: ByteArray): Int {
    val minLen = minOf(this.size, other.size)
    for (i in 0 until minLen) {
        val a = this[i].toInt() and 0xFF
        val b = other[i].toInt() and 0xFF
        if (a != b) return if (a < b) -1 else 1
    }
    return this.size.compareTo(other.size)
}

fun ByteArray.comparePackedKey(other: ByteArray, schema: KeySchema): Int{
    var offset1 = 0
    var offset2 = 0
    for (column in schema.columns){
        val byte1 = this[offset1]
        val byte2 = other[offset2]

        val isNull1 = byte1 == 0x00.toByte()
        val isNull2 = byte2 == 0x00.toByte()
        if (isNull1 && isNull2){
            offset1++
            offset2++
            continue
        }

        if (isNull1 && !isNull2) return if (column.descending) 1 else -1
        if (!isNull1 && isNull2) return if (column.descending) -1 else 1
        offset1++
        offset2++

        val (cmp, consumed1, consumed2) = comparePackedItem(this, offset1, other, offset2, column)
        if (cmp != 0) return cmp
        offset1 += consumed1
        offset2 += consumed2
    }
    return 0
}

fun comparePackedItem(bytes1: ByteArray, offset1: Int, bytes2: ByteArray, offset2: Int, column: Column): Triple<Int, Int, Int>{
    fun extractBytes(bytes: ByteArray, offset: Int, length: Int): ByteArray{
        return bytes.copyOfRange(offset, offset + length)
    }

    return when (column.type){
        ColumnType.INT -> {
            val (value1, length1) = decodeVarInt(bytes1, offset1, column.descending)
            val (value2, length2) = decodeVarInt(bytes2, offset2, column.descending)
            Triple(value1.compareTo(value2), length1, length2)
        }
        ColumnType.STRING, ColumnType.BYTES -> {
            val (len1, lenBytes1) = decodeVarInt(bytes1, offset1, column.descending)
            val (len2, lenBytes2) = decodeVarInt(bytes2, offset2, column.descending)
            val extractedBytes1 = extractBytes(bytes1, offset1 + lenBytes1, len1)
            val extractedBytes2 = extractBytes(bytes2, offset2 + lenBytes2, len2)
            Triple(extractedBytes1.compareTo(extractedBytes2), lenBytes1 + len1, lenBytes2 + len2)
        }
        ColumnType.LONG, ColumnType.DOUBLE, ColumnType.LOCAL_DATE_TIME, ColumnType.INSTANT -> {
            val len = 8
            val extractedBytes1 = extractBytes(bytes1, offset1, len)
            val extractedBytes2 = extractBytes(bytes2, offset2, len)
            Triple(extractedBytes1.compareTo(extractedBytes2), len, len)
        }
        ColumnType.FLOAT -> {
            val len = 4
            val extractedBytes1 = extractBytes(bytes1, offset1, len)
            val extractedBytes2 = extractBytes(bytes2, offset2, len)
            Triple(extractedBytes1.compareTo(extractedBytes2), len, len)
        }
        ColumnType.SHORT -> {
            val len = 2
            val extractedBytes1 = extractBytes(bytes1, offset1, len)
            val extractedBytes2 = extractBytes(bytes2, offset2, len)
            Triple(extractedBytes1.compareTo(extractedBytes2), len, len)
        }

        ColumnType.BOOLEAN, ColumnType.BYTE -> {
            val len = 1
            val extractedBytes1 = extractBytes(bytes1, offset1, len)
            val extractedBytes2 = extractBytes(bytes2, offset2, len)
            Triple(extractedBytes1.compareTo(extractedBytes2), len, len)
        }

        ColumnType.LOCAL_DATE -> {
            val (value1, length1) = decodeVarInt(bytes1, offset1, column.descending)
            val (value2, length2) = decodeVarInt(bytes2, offset2, column.descending)
            Triple(value1.compareTo(value2), length1, length2)
        }
        ColumnType.UUID -> {
            val len = 16
            val extractedBytes1 = extractBytes(bytes1, offset1, len)
            val extractedBytes2 = extractBytes(bytes2, offset2, len)
            Triple(extractedBytes1.compareTo(extractedBytes2), len, len)
        }
    }
}