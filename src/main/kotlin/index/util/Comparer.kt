package index.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Compare key of this, other
 * this < other -> result < 0
 * this == other -> result = 0
 * this > other -> result > 0
 * if descending=True -> result = result * (-1)
 * */
fun List<Any?>.compareUnpackedKey(otherKey: List<Any?>, schema: KeySchema): Int{
    for(idx in schema.columns.indices){
        val column = schema.columns[idx]
        val value1 = this[idx]
        val value2 = otherKey.getOrNull(idx)

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
                    ColumnType.INT -> (value1 as Int).compareTo(value2 as Int)
                    ColumnType.BYTE -> (value1 as Byte).compareTo(value2 as Byte)
                    ColumnType.LONG -> (value1 as Long).compareTo(value2 as Long)
                    ColumnType.LOCAL_DATE -> (value1 as LocalDate).compareTo(value2 as LocalDate)
                    ColumnType.LOCAL_DATE_TIME -> (value1 as LocalDateTime).compareTo(value2 as LocalDateTime)
                    ColumnType.UUID -> (value1 as UUID).compareTo(value2 as UUID)
                    ColumnType.INSTANT -> (value1 as Instant).compareTo(value2 as Instant)
                    ColumnType.FLOAT -> (value1 as Float).compareTo(value2 as Float)
                    ColumnType.SHORT -> (value1 as Short).compareTo(value2 as Short)
                    ColumnType.DOUBLE -> (value1 as Double).compareTo(value2 as Double)
                    ColumnType.BOOLEAN -> (value1 as Boolean).compareTo(value2 as Boolean)
                }
            }
        }
        if (comparison != 0) return if (column.descending) -comparison else comparison
    }
    return 0
}

/**
 * 사전식 정렬을 위한 바이트 배열 비교 (unsigned)
 * 0xFF: 0b11111111
 * */
fun ByteArray.compareTo(other: ByteArray, descending: Boolean=false): Int {
    val minLen = minOf(this.size, other.size)
    for (i in 0 until minLen) {
        val a = this[i].toInt() and 0xFF
        val b = other[i].toInt() and 0xFF
        if (a != b){
            return if(a < b){
                if (descending) 1 else -1
            }else{
                if (descending) -1 else 1
            }
        }
    }
    return if(!descending) this.size.compareTo(other.size) else -this.size.compareTo(other.size)
}