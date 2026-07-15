package index.serializer

import index.exception.SerializerException
import index.util.*
import java.lang.IndexOutOfBoundsException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*


abstract class BaseKeySerializer<K>(protected val schema: KeySchema): KeySerializer<K> {
    protected fun packKeyItem(key: Any?, column: Column): ByteArray{
        if(key == null) return byteArrayOf(0x00)
        val serialized = when (column.type){
            ColumnType.BOOLEAN -> {
                val packedKey = (key as Boolean).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }

            ColumnType.BYTE -> {
                val packedKey = (key as Byte).encodeSortable()
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
                val packedKey = (key as String).encodeSortable(column.collation)
                (byteArrayOf(0x01)) + packedKey
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
                val packedKey = (key as UUID).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
            ColumnType.BYTES -> {
                val packedKey = (key as ByteArray).encodeSortable()
                (byteArrayOf(0x01)) + packedKey
            }
        }
        return if(column.descending) serialized.invert() else serialized
    }

    protected fun unpackKeyItem(bytes: ByteArray, offset: Int, column: Column): Pair<Any?, Int> {
        var position = offset
        val bytesInverted = if(column.descending) bytes.invert() else bytes
        val nullFlag = try {
            bytesInverted[position++]
        } catch( exception: IndexOutOfBoundsException) {
            throw SerializerException.InvalidBytesException(exception)
        }
        if (nullFlag.toInt() == 0x00) return null to 1

        val columnType = column.type

        fun readVarType(bytes: ByteArray): ByteArray{
            val terminator = 0x00
            val escapeSequence = 0xFF
            var size = 0
            val startPosition = position
            for(byteIdx in position until bytes.size){
                // -128 ~ 127 의 범위를 0 ~ 255 로 변환
                val byte = bytes[byteIdx].toInt() and 0xFF
                if(byte == terminator &&
                    byteIdx + 1 < bytes.size &&
                    (bytes[byteIdx + 1].toInt() and 0xFF) == escapeSequence) continue
                else if(byte == terminator){
                    size = byteIdx - position + 1
                    position = byteIdx + 1
                    break
                }
            }
            return bytes.copyOfRange(startPosition, startPosition + size)
        }

        val result: Any? = when (columnType){
            ColumnType.BOOLEAN -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableBoolean()
            }
            ColumnType.BYTE -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableByte()
            }
            ColumnType.SHORT -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableShort()
            }
            ColumnType.INT -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableInt()
            }
            ColumnType.LONG -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableLong()
            }
            ColumnType.FLOAT -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableFloat()
            }
            ColumnType.DOUBLE -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableDouble()
            }

            ColumnType.STRING -> {
                val bytes = readVarType(bytesInverted)
                bytes.decodeSortableString(column.collation)
            }

            ColumnType.LOCAL_DATE -> {
                val array = readVarType(bytesInverted)
                val epochDay = array.decodeSortableLong()
                LocalDate.ofEpochDay(epochDay)
            }

            ColumnType.LOCAL_DATE_TIME -> {
                val array = readVarType(bytesInverted)
                val epochSecond = array.decodeSortableLong()
                LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC)
            }

            ColumnType.INSTANT -> {
                val array = readVarType(bytesInverted)
                val epochSecond = array.decodeSortableLong()
                Instant.ofEpochSecond(epochSecond)
            }

            ColumnType.UUID -> {
                val array = readVarType(bytesInverted)
                array.decodeSortableUUID()
            }
            ColumnType.BYTES -> {
                val bytes = readVarType(bytesInverted)
                bytes.decodeSortableByteArray()
            }
        }
        return result to (position - offset)
    }
}