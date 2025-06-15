package index.util

import mu.KotlinLogging
import java.lang.IndexOutOfBoundsException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/*
* page 로 넘어가게 되면 page 크기를 기준으로 고정 크기 buffer 사용 할 것
* */
object KeyTool {
    private val log = KotlinLogging.logger {}

    fun pack(keyList: List<Any?>, schema: KeySchema): ByteArray{
        require(keyList.size <= schema.columns.size) { "Too many key values for schema" }
        return buildList{
            for (idx in keyList.indices){
                val packed = packKeyItem(keyList[idx], schema.columns[idx])
                addAll(packed.toList())
            }
        }.toByteArray()
    }

    fun unpack(bytes: ByteArray, schema: KeySchema): List<Any?>{
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
            log.info { "unpacked keys: $unpackedKeys" }
        }
        return unpackedKeys
    }

    private fun packKeyItem(key: Any?, column: Column): ByteArray{
        if(key == null) return byteArrayOf(0x00)
        return when (column.type){
            ColumnType.BOOLEAN -> {
                val packedKey  = byteArrayOf(if (key as Boolean) 1 else 0)
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }

            ColumnType.BYTE -> {
                val packedKey = byteArrayOf(key as Byte)
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }

            ColumnType.SHORT -> {
                val packedKey = ByteBuffer.allocate(2).putShort(key as Short).array()
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.INT -> {
                val packedKey = encodeVarInt(key as Int)
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.LONG -> {
                val packedKey = ByteBuffer.allocate(8).putLong(key as Long).array()
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.FLOAT -> {
                val packedKey = ByteBuffer.allocate(4).putFloat(key as Float).array()
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.DOUBLE -> {
                val packedKey = ByteBuffer.allocate(8).putDouble(key as Double).array()
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.STRING -> {
                val rawString = key as String
                val bytes = column.collation?.getCollationKey(rawString)?.toByteArray() ?: rawString.toByteArray(StandardCharsets.UTF_8)
                (byteArrayOf(0x01)) + encodeVarInt(bytes.size) + bytes.invert(descending=column.descending)
            }

            ColumnType.LOCAL_DATE -> {
                val packedKey = encodeVarInt((key as LocalDate).toEpochDay().toInt())
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.LOCAL_DATE_TIME -> {
                val packedKey = ByteBuffer.allocate(8).putLong((key as LocalDateTime).toEpochSecond(ZoneOffset.UTC)).array()
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.INSTANT -> {
                val packedKey = ByteBuffer.allocate(8).putLong((key as Instant).epochSecond).array()
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.UUID -> {
                val packedKey = ByteBuffer.allocate(16)
                    .putLong((key as UUID).mostSignificantBits)
                    .putLong(key.leastSignificantBits)
                    .array()
                (byteArrayOf(0x01)) + packedKey.invert(column.descending)
            }
            ColumnType.BYTES -> {
                val bytes = key as ByteArray
                (byteArrayOf(0x01)) + encodeVarInt(bytes.size) + bytes.invert(descending=column.descending)
            }
        }
    }

    private fun unpackKeyItem(bytes: ByteArray, offset: Int, column: Column): Pair<Any?, Int> {
        var position = offset
        val nullFlag = try { bytes[position++] } catch( exception: IndexOutOfBoundsException ) { throw exception}
        if (nullFlag.toInt() == 0x00) return null to 1

        val descending = column.descending
        val columnType = column.type

        fun readBytes(length: Int): ByteArray {
            val slice = bytes.copyOfRange(position, position + length)
            position += length
            return slice.invert(descending)
        }

        val result: Any? = when (columnType){
            ColumnType.BOOLEAN -> bytes[position++].toInt() != 0
            ColumnType.BYTE -> bytes[position++]
            ColumnType.SHORT -> {
                val array = readBytes(2)
                ByteBuffer.wrap(array).short
            }
            ColumnType.INT -> {
                val (value, size) = decodeVarInt(bytes, position, descending = column.descending)
                position += size
                value
            }
            ColumnType.LONG -> {
                val array = readBytes(8)
                ByteBuffer.wrap(array).long
            }
            ColumnType.FLOAT -> {
                val array = readBytes(4)
                ByteBuffer.wrap(array).float
            }
            ColumnType.DOUBLE -> {
                val array = readBytes(8)
                ByteBuffer.wrap(array).double
            }

            ColumnType.STRING -> {
                val (len, lenBytes) = decodeVarInt(bytes, position)
                position += lenBytes
                val strBytes = readBytes(len)

                if (column.collation != null){
                    "[CollationKey(${strBytes.joinToString(" ")})]"
                } else{
                    strBytes.toString(StandardCharsets.UTF_8)
                }
            }

            ColumnType.LOCAL_DATE -> {
                val (epochDay, lenBytes) = decodeVarInt(bytes, position, descending = column.descending)
                position += lenBytes
                LocalDate.ofEpochDay(epochDay.toLong())
            }

            ColumnType.LOCAL_DATE_TIME -> {
                val array = readBytes(8)
                LocalDateTime.ofEpochSecond(ByteBuffer.wrap(array).long, 0, ZoneOffset.UTC)
            }

            ColumnType.INSTANT -> {
                val array = readBytes(8)
                Instant.ofEpochSecond(ByteBuffer.wrap(array).long)
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