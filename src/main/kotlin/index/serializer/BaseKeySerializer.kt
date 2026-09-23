package index.serializer

import exception.IndexException
import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import util.EngineErrorDetail
import util.decodeSortableBoolean
import util.decodeSortableByte
import util.decodeSortableByteArray
import util.decodeSortableDouble
import util.decodeSortableFloat
import util.decodeSortableInt
import util.decodeSortableLong
import util.decodeSortableShort
import util.decodeSortableString
import util.decodeSortableUUID
import util.encodeSortable
import util.invert
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

/**
 * Shared single-column encode/decode logic for [KeySerializer] implementations (see
 * [MultiColumnKeySerializer] for how these are combined into a full multi-column key).
 *
 * Every column's encoding is byte-comparable (memcmp-style): typed values are converted to a
 * form where unsigned byte comparison matches the type's natural ordering (`encodeSortable`, see
 * `util/Encoder.kt`), DESC columns are then bit-flipped so byte-ascending order becomes
 * value-descending, and NULL is placed as the smallest possible byte on either side so it sorts
 * first (ASC) or last (DESC) — MySQL/SQLite convention. See `docs/index/range-scan-design.md`.
 * */
abstract class BaseKeySerializer<K>(
    protected val schema: IndexKeySchema,
) : KeySerializer<K> {
    /**
     * Encodes one column's [key] value for [indexColumn], byte-comparable and DESC-aware.
     *
     * Layout: `null` -> a single byte, `0x00` (ASC) or `0xFF` (DESC) — the smallest byte possible
     * for ASC (so NULL sorts first) and the largest possible for DESC (so NULL sorts last),
     * matching MySQL/SQLite's NULLS FIRST-ASC/NULLS LAST-DESC convention. Non-null -> a `0x01`
     * presence flag followed by the type's `encodeSortable` bytes, then the *whole thing* (flag
     * included) bit-flipped if [IndexColumn.descending] — flipping the presence flag too is what
     * keeps it correctly ordered relative to the (never-flipped) DESC-NULL byte: `0x01` flips to
     * `0xFE`, which is still less than NULL's `0xFF`, so NULL correctly stays last.
     * */
    protected fun packKeyItem(
        key: Any?,
        indexColumn: IndexColumn,
    ): ByteArray {
        if (key == null) {
            return if (indexColumn.descending) byteArrayOf(0xFF.toByte()) else byteArrayOf(0x00)
        }
        val serialized =
            when (indexColumn.type) {
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
                    val packedKey = (key as String).encodeSortable(indexColumn.collation)
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
                    val packedKey = (key as Uuid).encodeSortable()
                    (byteArrayOf(0x01)) + packedKey
                }

                ColumnType.BYTES -> {
                    val packedKey = (key as ByteArray).encodeSortable()
                    (byteArrayOf(0x01)) + packedKey
                }
            }
        return if (indexColumn.descending) serialized.invert() else serialized
    }

    /**
     * Inverse of [packKeyItem]: decodes the column at [indexColumn] starting at [offset] within
     * the larger multi-column [bytes] buffer.
     *
     * Un-flips first (whole [bytes], not just this column's slice — wasteful but harmless, since
     * only this column's own range is read afterward) if [IndexColumn.descending], which is why a
     * plain `0x00` check for the presence flag works uniformly for both directions: a DESC NULL's
     * raw `0xFF` becomes `0x00` once un-flipped, same as an ASC NULL's raw `0x00` un-changed.
     *
     * `readVarType` finds this column's own end by scanning for a bare `0x00` terminator (one not
     * immediately followed by the `0x00 0xFF` escape sequence [util.escapeZeroBytes] produces for
     * a literal zero byte inside the value) — this is what lets fixed- and variable-width types
     * share the same "keep reading until terminator" logic without knowing each type's width.
     *
     * @return The decoded value (or null), and how many bytes of [bytes] starting at [offset] this
     *   column actually consumed — the caller advances its own offset by exactly that.
     * */
    protected fun unpackKeyItem(
        bytes: ByteArray,
        offset: Int,
        indexColumn: IndexColumn,
    ): Pair<Any?, Int> {
        var position = offset
        val bytesInverted = if (indexColumn.descending) bytes.invert() else bytes
        val nullFlag =
            try {
                bytesInverted[position++]
            } catch (exception: IndexOutOfBoundsException) {
                throw IndexException.InvalidBytes(
                    EngineErrorDetail(
                        reason = "Invalid bytes for serialization/deserialization.",
                    ),
                    exception,
                )
            }
        if (nullFlag.toInt() == 0x00) return null to 1

        val columnType = indexColumn.type

        fun readVarType(bytes: ByteArray): ByteArray {
            val terminator = 0x00
            val escapeSequence = 0xFF
            var size = 0
            val startPosition = position
            for (byteIdx in position until bytes.size) {
                // Convert the -128..127 range to 0..255.
                val byte = bytes[byteIdx].toInt() and 0xFF
                if (byte == terminator &&
                    byteIdx + 1 < bytes.size &&
                    (bytes[byteIdx + 1].toInt() and 0xFF) == escapeSequence
                ) {
                    continue
                } else if (byte == terminator) {
                    size = byteIdx - position + 1
                    position = byteIdx + 1
                    break
                }
            }
            return bytes.copyOfRange(startPosition, startPosition + size)
        }

        val result: Any? =
            when (columnType) {
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
                    bytes.decodeSortableString(indexColumn.collation)
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
