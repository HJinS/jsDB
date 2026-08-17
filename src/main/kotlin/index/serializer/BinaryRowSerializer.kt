package index.serializer

import index.util.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class BinaryRowSerializer(private val rowSchema: RowColumnSchema): ValueSerializer<List<Any?>>{
    override fun serialize(value: List<Any?>): ByteArray {
        require(value.size == rowSchema.rowColumns.size) { "Invalid length values" }
        val tempArray = ArrayList<ByteArray>(rowSchema.rowColumns.size)
        var totalSize = 0
        for (idx in rowSchema.rowColumns.indices) {
            val valueItem = value[idx]
            val schema = rowSchema.rowColumns[idx]
            if (valueItem == null) {
                tempArray.add(byteArrayOf(0x00))
                totalSize += 1
                continue
            }
            val serialized = when (schema.type) {
                ColumnType.BOOLEAN -> byteArrayOf(0x01, if (valueItem as Boolean) 1 else 0)
                ColumnType.BYTE -> byteArrayOf(0x01, valueItem as Byte)
                ColumnType.SHORT -> byteArrayOf(0x01) + ByteBuffer.allocate(Short.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putShort(valueItem as Short).array()
                ColumnType.INT -> byteArrayOf(0x01) + ByteBuffer.allocate(Int.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putInt(valueItem as Int).array()
                ColumnType.LONG -> byteArrayOf(0x01) + ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putLong(valueItem as Long).array()
                ColumnType.FLOAT -> byteArrayOf(0x01) + ByteBuffer.allocate(Float.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putFloat(valueItem as Float).array()
                ColumnType.DOUBLE -> byteArrayOf(0x01) + ByteBuffer.allocate(Double.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putDouble(valueItem as Double).array()
                ColumnType.STRING -> byteArrayOf(0x01) + (valueItem as String).encodeBinary()
                ColumnType.BYTES -> byteArrayOf(0x01) + (valueItem as ByteArray).encodeBinary()
                ColumnType.LOCAL_DATE -> byteArrayOf(0x01) + ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putLong((valueItem as LocalDate).toEpochDay()).array()
                ColumnType.LOCAL_DATE_TIME -> byteArrayOf(0x01) + ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putLong((valueItem as LocalDateTime).toEpochSecond(ZoneOffset.UTC)).array()
                ColumnType.INSTANT -> byteArrayOf(0x01) + ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putLong((valueItem as Instant).epochSecond).array()
                ColumnType.UUID -> {
                    val uuidValue = valueItem as UUID
                    byteArrayOf(0x01) + ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                        .putLong(uuidValue.mostSignificantBits).putLong(uuidValue.leastSignificantBits).array()
                }
            }
            tempArray.add(serialized)
            totalSize += serialized.size
        }
        val resultArray = ByteArray(totalSize)
        var offset = 0
        for (byteArray in tempArray) {
            System.arraycopy(byteArray, 0, resultArray, offset, byteArray.size)
            offset += byteArray.size
        }
        return resultArray
    }

    override fun deserialize(bytes: ByteArray): List<Any?> {
        val result = mutableListOf<Any?>()
        var offset = 0
        for (column in rowSchema.rowColumns) {
            val nullFlag = bytes[offset++]
            if (nullFlag == 0x00.toByte()) {
                result.add(null)
                continue
            }
            val (value, consumed) = readValue(bytes, offset, column.type)
            result.add(value)
            offset += consumed
        }
        return result
    }

    private fun readValue(bytes: ByteArray, offset: Int, type: ColumnType): Pair<Any, Int> {
        return when (type) {
            ColumnType.BOOLEAN -> (bytes[offset].toInt() == 1) to 1
            ColumnType.BYTE -> bytes[offset] to 1
            ColumnType.SHORT -> ByteBuffer.wrap(bytes, offset, Short.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).short to Short.SIZE_BYTES
            ColumnType.INT -> ByteBuffer.wrap(bytes, offset, Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).int to Int.SIZE_BYTES
            ColumnType.LONG -> ByteBuffer.wrap(bytes, offset, Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).long to Long.SIZE_BYTES
            ColumnType.FLOAT -> ByteBuffer.wrap(bytes, offset, Float.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).float to Float.SIZE_BYTES
            ColumnType.DOUBLE -> ByteBuffer.wrap(bytes, offset, Double.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).double to Double.SIZE_BYTES
            ColumnType.STRING -> bytes.decodeBinaryString(offset)
            ColumnType.BYTES -> bytes.decodeBinaryByteArray(offset)
            ColumnType.LOCAL_DATE -> {
                val epochDay = ByteBuffer.wrap(bytes, offset, Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).long
                LocalDate.ofEpochDay(epochDay) to Long.SIZE_BYTES
            }
            ColumnType.LOCAL_DATE_TIME -> {
                val epochSecond = ByteBuffer.wrap(bytes, offset, Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).long
                LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC) to Long.SIZE_BYTES
            }
            ColumnType.INSTANT -> {
                val epochSecond = ByteBuffer.wrap(bytes, offset, Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).long
                Instant.ofEpochSecond(epochSecond) to Long.SIZE_BYTES
            }
            ColumnType.UUID -> {
                val buffer = ByteBuffer.wrap(bytes, offset, 16).order(ByteOrder.BIG_ENDIAN)
                UUID(buffer.long, buffer.long) to 16
            }
        }
    }
}
