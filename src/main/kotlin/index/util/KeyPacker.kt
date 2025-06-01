package index.util

import java.io.ByteArrayOutputStream
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
class KeyPacker {
    private fun packKeyItem(key: Any?, column: Column): ByteArray{
        if(key == null) return byteArrayOf(0x00)
        val packedKey = when (column.type){
            ColumnType.BOOLEAN -> byteArrayOf(if (key as Boolean) 1 else 0)

            ColumnType.BYTE -> byteArrayOf(key as Byte)

            ColumnType.SHORT -> ByteBuffer.allocate(2).putShort(key as Short).array()
            ColumnType.INT -> encodeVarInt(key as Int)
            ColumnType.LONG -> ByteBuffer.allocate(8).putLong(key as Long).array()

            ColumnType.FLOAT -> ByteBuffer.allocate(4).putFloat(key as Float).array()
            ColumnType.DOUBLE -> ByteBuffer.allocate(8).putDouble(key as Double).array()

            ColumnType.STRING -> {
                val rawString = key as String
                val bytes = column.collation?.getCollationKey(rawString)?.toByteArray() ?: rawString.toByteArray(StandardCharsets.UTF_8)
                encodeVarInt(bytes.size) + bytes
            }

            ColumnType.LOCAL_DATE -> encodeVarInt((key as LocalDate).toEpochDay().toInt())
            ColumnType.LOCAL_DATE_TIME -> ByteBuffer.allocate(8)
                .putLong((key as LocalDateTime).toEpochSecond(ZoneOffset.UTC)).array()
            ColumnType.INSTANT -> ByteBuffer.allocate(8).putLong((key as Instant).epochSecond).array()

            ColumnType.UUID -> ByteBuffer.allocate(16)
                .putLong((key as UUID).mostSignificantBits)
                .putLong(key.leastSignificantBits)
                .array()
            ColumnType.BYTES -> {
                val bytes = key as ByteArray
                encodeVarInt(bytes.size) + bytes
            }
        }
        return (byteArrayOf(0x01) + packedKey).invert(column.descending)
    }

    fun pack(keyList: List<Any?>, schema: KeySchema): ByteArray{
        require(keyList.size == schema.columns.size) { "Key values and schema column count mismatch" }
        return buildList{
            for (idx in keyList.indices){
                val packed = packKeyItem(keyList[idx], schema.columns[idx])
                addAll(packed.toList())
            }
        }.toByteArray()
    }

}