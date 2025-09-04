package index.serializer

import index.btree.logger
import index.util.*
import java.lang.IndexOutOfBoundsException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*


abstract class BaseKeySerializer<K>(protected val schema: KeySchema): KeySerializer<K> {
    protected fun packKeyItem(key: Any?, column: Column): ByteArray{
        if(key == null) return byteArrayOf(0x00)
        return when (column.type){
            ColumnType.BOOLEAN -> {
                val packedKey  = byteArrayOf(if (key as Boolean) 1 else 0)
                (byteArrayOf(0x01)) + packedKey
            }

            ColumnType.BYTE -> {
                val packedKey = byteArrayOf(key as Byte)
                (byteArrayOf(0x01)) + packedKey
            }

            ColumnType.SHORT -> {
                val packedKey = (key as Short).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.INT -> {
                val packedKey = (key as Int).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.LONG -> {
                val packedKey = (key as Long).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.FLOAT -> {
                val packedKey = (key as Float).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.DOUBLE -> {
                val packedKey = (key as Double).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.STRING -> {
                val rawString = key as String
                val bytes = column.collation?.getCollationKey(rawString)?.toByteArray() ?: rawString.toByteArray(
                    StandardCharsets.UTF_8)
                (byteArrayOf(0x01)) + encodeVarInt(bytes.size) + bytes
            }

            ColumnType.LOCAL_DATE -> {
                val epochDay = (key as LocalDate).toEpochDay()
                val packedKey = epochDay.encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.LOCAL_DATE_TIME -> {
                val epochSecond = (key as LocalDateTime).toEpochSecond(ZoneOffset.UTC)
                val packedKey = epochSecond.encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.INSTANT -> {
                val epochSecond = (key as Instant).epochSecond
                val packedKey = epochSecond.encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.UUID -> {
                val packedKey = ByteBuffer.allocate(16)
                    .putLong((key as UUID).mostSignificantBits)
                    .putLong(key.leastSignificantBits)
                    .array()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.BYTES -> {
                val bytes = key as ByteArray
                (byteArrayOf(0x01)) + encodeVarInt(bytes.size) + bytes
            }
        }
    }

    protected fun unpackKeyItem(bytes: ByteArray, offset: Int, column: Column): Pair<Any?, Int> {
        var position = offset
        val nullFlag = try { bytes[position++] } catch( exception: IndexOutOfBoundsException) { throw exception}
        if (nullFlag.toInt() == 0x00) return null to 1

        val columnType = column.type

        fun readBytes(length: Int): ByteArray {
            val slice = bytes.copyOfRange(position, position + length)
            position += length
            return slice
        }

        val result: Any? = when (columnType){
            ColumnType.BOOLEAN -> bytes[position++].toInt() != 0
            ColumnType.BYTE -> bytes[position++]
            ColumnType.SHORT -> {
                val array = readBytes(2)
                array.decodeSortableShort()
            }
            ColumnType.INT -> {
                val array = readBytes(4)
                array.decodeSortableInt()
            }
            ColumnType.LONG -> {
                val array = readBytes(8)
                array.decodeSortableLong()
            }
            ColumnType.FLOAT -> {
                val array = readBytes(4)
                array.decodeSortableFloat()
            }
            ColumnType.DOUBLE -> {
                val array = readBytes(8)
                array.decodeSortableDouble()
            }

            ColumnType.STRING -> {
                val (len, lenBytes) = decodeVarInt(bytes, position)
                position += lenBytes
                val strBytes = readBytes(len)

                if (column.collation != null){
                    val result = "[CollationKey(${strBytes.joinToString(" ")})]"
                    logger.info { "UnpackKey: $result" }
                    result
                } else{
                    strBytes.toString(StandardCharsets.UTF_8)
                }
            }

            ColumnType.LOCAL_DATE -> {
                val array = readBytes(8)
                val epochDay = array.decodeSortableLong()
                LocalDate.ofEpochDay(epochDay)
            }

            ColumnType.LOCAL_DATE_TIME -> {
                val array = readBytes(8)
                val epochSecond = array.decodeSortableLong()
                LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC)
            }

            ColumnType.INSTANT -> {
                val array = readBytes(8)
                val epochSecond = array.decodeSortableLong()
                Instant.ofEpochSecond(epochSecond)
            }

            ColumnType.UUID -> {
                val array = readBytes(16)
                val bb = ByteBuffer.wrap(array)
                UUID(bb.long, bb.long)
            }
            ColumnType.BYTES -> {
                val (len, lenBytes) = decodeVarInt(bytes, position)
                position += lenBytes
                readBytes(len)
            }
        }
        return result to (position - offset)
    }
}