package index.util

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
        return (byteArrayOf(0x01)) + packedKey.invert(column.descending)
    }

    fun unpack(bytes: ByteArray, schema: KeySchema): List<Any?>{
        val unpackedKeys = mutableListOf<Any?>()
        var offset = 0

        for (column in schema.columns){
            if (offset >= bytes.size) break

            if(bytes[offset++] == 0x00.toByte()) {
                unpackedKeys.add(null)
                continue
            }

            val raw = mutableListOf<Byte>()
            while (offset < bytes.size && unpackedKeys.size < schema.columns.size){
                raw.add(bytes[offset++])
                if(
                    column.type != ColumnType.BYTES &&
                    column.type != ColumnType.STRING &&
                    column.type != ColumnType.INT &&
                    column.type != ColumnType.LOCAL_DATE
                ) break // fixed-width types
                // for VarInt-based, decode below
            }

            val restored = raw.toByteArray().invert(column.descending)

            val value = when (column.type){
                ColumnType.BOOLEAN -> restored[0] != 0.toByte()
                ColumnType.BYTE -> restored[0]
                ColumnType.SHORT -> ByteBuffer.wrap(restored).short
                ColumnType.INT -> decodeVarInt(restored).first
                ColumnType.LONG -> ByteBuffer.wrap(restored).long
                ColumnType.FLOAT -> ByteBuffer.wrap(restored).float
                ColumnType.DOUBLE -> ByteBuffer.wrap(restored).double

                ColumnType.STRING -> {
                    val (len, lenBytes) = decodeVarInt(restored)
                    val strBytes = restored.copyOfRange(lenBytes, lenBytes + len)
                    strBytes.toString(StandardCharsets.UTF_8)
                }

                ColumnType.LOCAL_DATE -> {
                    val (days, _) = decodeVarInt(restored)
                    LocalDate.ofEpochDay(days.toLong())
                }

                ColumnType.LOCAL_DATE_TIME -> {
                    val seconds = ByteBuffer.wrap(restored).long
                    LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC)
                }

                ColumnType.INSTANT -> Instant.ofEpochSecond(ByteBuffer.wrap(restored).long)

                ColumnType.UUID -> {
                    val bb = ByteBuffer.wrap(restored)
                    UUID(bb.long, bb.long)
                }
                ColumnType.BYTES -> {
                    val (len, lenBytes) = decodeVarInt(restored)
                    restored.copyOfRange(lenBytes, lenBytes + len)
                }
            }
            unpackedKeys.add(value)
        }
        return unpackedKeys
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