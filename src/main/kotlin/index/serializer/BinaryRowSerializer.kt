package index.serializer

import schema.*
import util.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.uuid.Uuid

/**
 * [ValueSerializer] for a full row (or, for a secondary index's value, just the primary key —
 * still just a `RowSchema`-shaped list either way). Since [ValueSerializer] bytes are never
 * compared (see its doc), this uses plain big-endian fixed-width encoding rather than the
 * byte-comparable scheme [BaseKeySerializer] uses for keys — no DESC bit-flipping needed, and
 * (for [ColumnType.STRING]/[ColumnType.BYTES]) a varint length prefix instead of
 * [BaseKeySerializer]'s `0x00`-terminator scanning, since there's no ordering to preserve here.
 *
 * ### Layout
 * A leading **null-bitmap**, `ceil(columnCount / 8)` bytes, followed by each **non-null**
 * column's own encoding back-to-back in schema order — a column whose bit is set contributes
 * *zero* bytes to the value section, not a placeholder:
 *
 * ```
 * +--------------+------------------------+------------------------+-----
 * | null-bitmap  | column 0 (if not null) | column 1 (if not null) | ...
 * | ceil(n/8) B  |     type-dependent     |     type-dependent     |
 * +--------------+------------------------+------------------------+-----
 * ```
 *
 * Each bitmap byte is MSB-first: column `idx` is null if bit `7 - (idx % 8)` of byte `idx / 8`
 * is set. Breaking that formula down:
 * - `idx / 8` — which bitmap *byte* holds column `idx` (8 columns per byte).
 * - `idx % 8` — column `idx`'s position *within* that byte, counting `0` for that byte's first
 *   column up to `7` for its last.
 * - `7 - (idx % 8)` — flips that into an actual bit position, counting from the MSB (bit 7) down
 *   to the LSB (bit 0). This is what "MSB-first" means here: a byte's *first* column (position 0)
 *   occupies its *highest* bit, not its lowest.
 *
 * So column 0 is a byte's `0x80` bit, column 1 is `0x40`, ..., column 7 is `0x01`, then column 8
 * starts the next byte the same way.
 *
 * Fixed-width types ([ColumnType.BOOLEAN], [ColumnType.BYTE], [ColumnType.SHORT], `INT`, `LONG`,
 * `FLOAT`, `DOUBLE`, `LOCAL_DATE`, `LOCAL_DATE_TIME`, `INSTANT`, `UUID`) are read back with a
 * fixed byte count per [ColumnType], so no length needs to be stored. [ColumnType.STRING]/
 * [ColumnType.BYTES] are variable-width and self-delimit with a leading varint length
 * (`encodeBinary`/`decodeBinaryString`/`decodeBinaryByteArray` in `util/Encoder.kt`).
 *
 * ### Worked example
 * Schema `[id: LONG (not null), name: STRING (nullable)]`, encoding `[1L, null]`:
 *
 * ```
 * byte:    0          1  2  3  4  5  6  7  8
 *         +----------+------------------------+
 *         | 01000000 | 00 00 00 00 00 00 00 01 |
 *         +----------+------------------------+
 *          null-bitmap        id = 1L (8 bytes, big-endian)
 * ```
 * Byte 0 is `0b01000000`: bit `7 - (1 % 8) = 6` is set, meaning column index 1 (`name`) is null
 * — so `name` contributes nothing after the `id` bytes, and the whole value is 9 bytes total.
 *
 * Encoding `[1L, "ab"]` instead (neither column null):
 *
 * ```
 * byte:    0          1                          9   10 11 12
 *         +----------+------------------------+----+-----+
 *         | 00000000 | 00 00 00 00 00 00 00 01 | 02 | 61 62 |
 *         +----------+------------------------+----+-----+
 *          null-bitmap    id = 1L (8 bytes)     len  "ab" (UTF-8)
 * ```
 * `name`'s bit is now clear, so its bytes follow `id`'s: a varint length (`0x02`) then the 2 raw
 * UTF-8 bytes — 12 bytes total.
 * */
class BinaryRowSerializer(private val rowSchema: RowSchema): ValueSerializer<List<Any?>>{
    override fun serialize(value: List<Any?>): ByteArray {
        require(value.size == rowSchema.rowColumns.size) { "Invalid length values" }
        val tempArray = ArrayList<ByteArray>(rowSchema.rowColumns.size)
        var totalSize = 0
        val totalMaskCount = ceil(rowSchema.rowColumns.size / 8.0).toInt()
        val nullFlag = ByteArray(totalMaskCount){ 0x00 }
        for (idx in rowSchema.rowColumns.indices) {
            val valueItem = value[idx]
            val schema = rowSchema.rowColumns[idx]
            if (valueItem == null) {
                require(schema.nullable) { "Column '${schema.name}' is not nullable" }
                val bytePosition = nullFlag[idx / 8]
                val bitPosition = (1 shl (7 - (idx % 8)))
                nullFlag[idx / 8] = (bytePosition.toInt() or bitPosition).toByte()
                continue
            }
            val serialized = when (schema.type) {
                ColumnType.BOOLEAN -> byteArrayOf(if (valueItem as Boolean) 1 else 0)
                ColumnType.BYTE -> byteArrayOf(valueItem as Byte)
                ColumnType.SHORT -> ByteBuffer.allocate(Short.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putShort(valueItem as Short).array()
                ColumnType.INT -> ByteBuffer.allocate(Int.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putInt(valueItem as Int).array()
                ColumnType.LONG -> ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putLong(valueItem as Long).array()
                ColumnType.FLOAT -> ByteBuffer.allocate(Float.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putFloat(valueItem as Float).array()
                ColumnType.DOUBLE -> ByteBuffer.allocate(Double.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putDouble(valueItem as Double).array()
                ColumnType.STRING -> (valueItem as String).encodeBinary()
                ColumnType.BYTES -> (valueItem as ByteArray).encodeBinary()
                ColumnType.LOCAL_DATE -> ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putLong((valueItem as LocalDate).toEpochDay()).array()
                ColumnType.LOCAL_DATE_TIME -> ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong((valueItem as LocalDateTime).toEpochSecond(ZoneOffset.UTC))
                    .array()
                ColumnType.INSTANT -> ByteBuffer.allocate(Long.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN).putLong((valueItem as Instant).epochSecond).array()
                ColumnType.UUID -> (valueItem as Uuid).toByteArray()
            }
            tempArray.add(serialized)
            totalSize += serialized.size
        }
        val resultArray = ByteArray(totalSize + totalMaskCount)
        var offset = 0
        System.arraycopy(nullFlag, 0, resultArray, offset, nullFlag.size)
        offset += totalMaskCount
        for (byteArray in tempArray) {
            System.arraycopy(byteArray, 0, resultArray, offset, byteArray.size)
            offset += byteArray.size
        }
        return resultArray
    }

    /** @return The decoded row values (nulls included, in schema order), and total bytes consumed. */
    override fun deserialize(bytes: ByteArray): Pair<List<Any?>, Int> {
        val result = mutableListOf<Any?>()
        var offset = 0
        val totalMaskCount = ceil(rowSchema.rowColumns.size / 8.0).toInt()
        offset += totalMaskCount
        val nullFlag = ByteArray(totalMaskCount)
        System.arraycopy(bytes, 0, nullFlag, 0, totalMaskCount)
        for (idx in rowSchema.rowColumns.indices) {
            val column = rowSchema.rowColumns[idx]
            val bytePosition = nullFlag[idx / 8]
            val bitPosition = (1 shl (7 - (idx % 8)))
            if((bytePosition.toInt() and bitPosition) != 0) {
                result.add(null)
                continue
            }
            val (value, consumed) = readValue(bytes, offset, column.type)
            result.add(value)
            offset += consumed
        }
        return result to offset
    }

    /**
     * Decodes one non-null column of [type] starting at [offset].
     * @return The value, and bytes consumed.
     * */
    private fun readValue(bytes: ByteArray, offset: Int, type: ColumnType): Pair<Any, Int> {
        return when (type) {
            ColumnType.BOOLEAN -> (bytes[offset].toInt() == 1) to 1
            ColumnType.BYTE -> bytes[offset] to 1
            ColumnType.SHORT -> ByteBuffer
                .wrap(bytes, offset, Short.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .short to Short.SIZE_BYTES
            ColumnType.INT -> ByteBuffer
                .wrap(bytes, offset, Int.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .int to Int.SIZE_BYTES
            ColumnType.LONG -> ByteBuffer
                .wrap(bytes, offset, Long.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .long to Long.SIZE_BYTES
            ColumnType.FLOAT -> ByteBuffer
                .wrap(bytes, offset, Float.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .float to Float.SIZE_BYTES
            ColumnType.DOUBLE -> ByteBuffer
                .wrap(bytes, offset, Double.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .double to Double.SIZE_BYTES
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
                Uuid.fromLongs(buffer.long, buffer.long) to 16
            }
        }
    }
}
