package util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.text.Collator
import kotlin.experimental.inv
import kotlin.experimental.xor
import kotlin.text.toByteArray
import kotlin.uuid.Uuid
import exception.IndexException


/**
 * Stores small numbers in a small amount of space and large numbers in more
 * - split into 7-bit chunks.
 * 1 -> 0x01
 * 300 -> 0xAC 0x02
 * Based on Unsigned LEB128.
 * ushr: shifts bits right.
 * 0x80: 0b10000000
 * 0x7F: 0b01111111
 *
 * * example)
 *  * original value: 10101010 10101010 00000000
 *  * first: 10000000
 *  * second: 10000000 11010100
 *  * third: 10000000 11010100 10101010
 *  * fourth: 10000000 11010100 10101010 00000101
 * */
fun encodeVarInt(value: Int): ByteArray{
    var v = value
    val output = mutableListOf<Byte>()
    do {
        var b = (v and 0x7F)
        v = v ushr 7 // Shift by 7 bits to check the next digit.
        if (v != 0) b = b or 0x80 // If nonzero, set the very first bit (MSB) to 1.
        output.add(b.toByte()) // Append the byte.
    } while (v != 0)
    return output.toByteArray()
}

/**
 * Inverse of [encodeVarInt].
 * shl: shifts bits left.
 * - 0xFF: 0b11111111
 * - 0x80: 0b10000000
 * - 0x7F: 0b01111111
 *
 * ```
 * example
 *  - original value: 10000000 11010100 10101010 00000101
 *  - first: 0000000
 *  - second: 1010100 0000000
 *  - third: 0101010 1010100 0000000
 *  - fourth: 0000101 0101010 1010100 0000000
 *  - fifth: 10101010 10101010 00000000
 * ```
 * */
fun decodeVarInt(bytes: ByteArray, offset: Int = 0): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var pos = offset

    while (true) {
        // Check whether we've run past the end of the array.
        if (pos >= bytes.size)
            throw IndexException.PositionOutOfBounds(
                EngineErrorDetail(
                    reason = "Position $pos should be less than total byte size ${bytes.size}."
                )
            )

        val byte = bytes[pos].toInt() and 0xFF
        // Grab the byte to decode from the ByteArray and convert it to an unsigned int (only the
        // last 8 bits are kept, accounting for sign extension during the int conversion).
        // Take its lower 7 bits, shift them left by the running total (0 the first time), and OR
        // them in. During encoding, higher-order digits end up further toward the end of the array.
        // If a byte's leading bit is 1, that means there are more digits following.
        result = result or ((byte and 0x7F) shl shift)
        pos++

        // If the MSB is 0, this was the last byte, so stop.
        if ((byte and 0x80) == 0) break
        shift += 7

        // Guard against excessive data that would overflow a 32-bit Int.
        if (shift >= 32) {
            throw IndexException.VarIntTooLong(EngineErrorDetail(reason = "VarInt is too long"))
        }
    }
    return result to (pos - offset)
}

/**
 * Indexes are sorted ascending by default. To sort a column DESC instead, the stored bytes are bit-inverted.
 * 0xFF = 0000 0000 0000 0000 0000 0000 1111 1111
 * */
fun ByteArray.invert(): ByteArray{
    val resultBytes = ByteArray(this.size)
    for(idx in this.indices){
        resultBytes[idx] = this[idx].inv()
    }
    return resultBytes
}

/**
 * Flips the leading sign bit to produce a sortable representation.
 * For ASC/DESC, this is used by multiplying the cmp result by -1
 *  - inverting could otherwise corrupt the result.
 * */
fun Int.encodeSortable(): ByteArray{
    val sortableBits = this xor Int.MIN_VALUE
    val bytes = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(sortableBits).array()
    return escapeZeroBytes(bytes)
}

fun Long.encodeSortable(): ByteArray{
    val sortableBits = this xor Long.MIN_VALUE
    val bytes = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putLong(sortableBits).array()
    return escapeZeroBytes(bytes)
}

fun Short.encodeSortable(): ByteArray{
    val sortableBits = this xor Short.MIN_VALUE
    val bytes = ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putShort(sortableBits).array()
    return escapeZeroBytes(bytes)
}

fun Byte.encodeSortable(): ByteArray {
    val sortableBits = this xor Byte.MIN_VALUE
    val bytes = byteArrayOf(sortableBits)
    return escapeZeroBytes(bytes)
}

fun Boolean.encodeSortable(): ByteArray {
    val rawByte = if(this) 1.toByte() else 0.toByte()
    return escapeZeroBytes(byteArrayOf(rawByte))
}

fun Uuid.encodeSortable(): ByteArray{
    return escapeZeroBytes(this.toByteArray())
}

/**
 * Takes the bit representation
 * - for a positive value, sets the top bit to 1
 * - for a negative value, flips every bit.
 * That's the packing.
 * For ASC/DESC, this is used by multiplying the cmp result by -1
 *  - inverting could otherwise corrupt the result.
 * */

fun Float.encodeSortable(): ByteArray{
    val bits = this.toRawBits()
    val sortableBits = if(this > 0f) bits xor Int.MIN_VALUE else bits.inv()
    val bytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(sortableBits).array()
    return escapeZeroBytes(bytes)
}

fun Double.encodeSortable(): ByteArray{
    val bits = this.toRawBits()
    val sortableBits = if(this > 0.0) bits xor Long.MIN_VALUE else bits.inv()
    val bytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(sortableBits).array()
    return escapeZeroBytes(bytes)
}


/**
 * Escapes any `0x00` byte as `0x00 0xFF`, and appends a termination mark.
 *
 * */
fun escapeZeroBytes(bytes: ByteArray): ByteArray{
    val length = bytes.size
    var zeroByteCount = 0
    for(idx in 0 until length){
        if(bytes[idx] == 0.toByte()) zeroByteCount++
    }

    // get extra byte for termination mark used later
    val resultBytes = ByteArray(length + zeroByteCount + 1)
    var sourceBytesIdx = 0
    var targetBytesIdx = 0
    var lengthToCopy = length

    while(sourceBytesIdx < length){
        var nextZeroIndex = -1

        if(zeroByteCount > 0 ){
            for(idx in sourceBytesIdx until length){
                if(bytes[idx] == 0.toByte()){
                    nextZeroIndex = idx
                    break
                }
            }
            lengthToCopy = if(nextZeroIndex == -1) length - sourceBytesIdx else nextZeroIndex - sourceBytesIdx
        }

        System.arraycopy(bytes, sourceBytesIdx, resultBytes, targetBytesIdx, lengthToCopy)
        targetBytesIdx += lengthToCopy
        if(nextZeroIndex == -1){
            break
        } else {
            resultBytes[targetBytesIdx++] = 0x00
            resultBytes[targetBytesIdx++] = 0xFF.toByte()
            sourceBytesIdx = nextZeroIndex + 1
        }
    }
    resultBytes[targetBytesIdx] = 0
    return resultBytes
}

/**
 * For string encoding, rather than encoding a length prefix, a terminator (`0x00`) is appended at
 * the end so lexicographic comparison works directly. If a `0x00` appears inside the string
 * itself, it's escaped as `0x00 0xFF`.
 * */
fun String.encodeSortable(collator: Collator?): ByteArray{
    val bytes = collator?.getCollationKey(this)?.toByteArray() ?: this.toByteArray(StandardCharsets.UTF_8)
    return escapeZeroBytes(bytes)
}

fun ByteArray.encodeSortable() = escapeZeroBytes(this)


fun ByteArray.decodeSortableInt(): Int{
    val unEscaped = unescapeZeroBytes(this)
    val sortableBits = ByteBuffer.wrap(unEscaped).order(ByteOrder.BIG_ENDIAN).int
    return sortableBits xor Int.MIN_VALUE
}

fun ByteArray.decodeSortableLong(): Long{
    val unEscaped = unescapeZeroBytes(this)
    val sortableBits = ByteBuffer.wrap(unEscaped).order(ByteOrder.BIG_ENDIAN).long
    return sortableBits xor Long.MIN_VALUE
}

fun ByteArray.decodeSortableShort(): Short{
    val unEscaped = unescapeZeroBytes(this)
    val sortableBits = ByteBuffer.wrap(unEscaped).order(ByteOrder.BIG_ENDIAN).short
    return sortableBits xor Short.MIN_VALUE
}


fun ByteArray.decodeSortableByte(): Byte {
    val unEscaped = unescapeZeroBytes(this)
    val byte = unEscaped[0]
    return byte xor Byte.MIN_VALUE
}

/**
 * Applies the encoding rule in reverse: since a positive value had its MSB set to 1, a negative
 * `sortableBits` (meaning the original was positive) is restored via xor.
 * */
fun ByteArray.decodeSortableFloat(): Float{
    val unEscaped = unescapeZeroBytes(this)
    val sortableBits = ByteBuffer.wrap(unEscaped).order(ByteOrder.BIG_ENDIAN).int
    val originalBits = if(sortableBits < 0) sortableBits xor Int.MIN_VALUE else sortableBits.inv()
    return Float.fromBits(originalBits)
}

fun ByteArray.decodeSortableDouble(): Double{
    val unEscaped = unescapeZeroBytes(this)
    val sortableBits = ByteBuffer.wrap(unEscaped).order(ByteOrder.BIG_ENDIAN).long
    val originalBits = if(sortableBits < 0) sortableBits xor Long.MIN_VALUE else sortableBits.inv()
    return Double.fromBits(originalBits)
}

fun ByteArray.decodeSortableBoolean(): Boolean {
    val unEscaped = unescapeZeroBytes(this)[0]
    return unEscaped.toInt() != 0
}


fun ByteArray.decodeSortableUUID(): Uuid{
    val unEscaped = unescapeZeroBytes(this)
    if(unEscaped.size != 16)
        throw IndexException.InvalidUUIDLength(
            EngineErrorDetail(
                reason = "UUID should be 16 bytes, but got ${unEscaped.size}"
            )
        )

    return Uuid.fromByteArray(unEscaped)
}

fun unescapeZeroBytes(bytes: ByteArray): ByteArray{
    var fromIdx = 0
    var toIdx = 0
    val size = bytes.size
    var validCharCount = 0
    while (fromIdx  < size){
        val byte = bytes[fromIdx]
        if(fromIdx + 1 < size && byte == 0x00.toByte() && bytes[fromIdx+1] == 0xFF.toByte()){
            fromIdx++
        } else if(byte == 0x00.toByte()) {
            break
        }
        bytes[toIdx] = byte
        validCharCount++
        toIdx++
        fromIdx++
    }
    val results = ByteArray(validCharCount)
    System.arraycopy(bytes, 0, results, 0, validCharCount)
    return results
}

/**
 * Inverse of [String.encodeSortable]. When [collator] is non-null, this is **not** actually
 * invertible — a `CollationKey`'s bytes can't be turned back into the original string, since the
 * transform is lossy by design (that's what makes it byte-comparable). Returns a hex-dump
 * placeholder in that case instead; see [schema.IndexColumn.collation] for where this matters.
 * */
fun ByteArray.decodeSortableString(collator: Collator?): String{
    val unescaped = unescapeZeroBytes(this)
    return if (collator != null){
        "[CollationKey(${unescaped.joinToString(""){ "%02x".format(it) }})]"
    } else{
        unescaped.toString(StandardCharsets.UTF_8)
    }
}

fun ByteArray.decodeSortableByteArray() = unescapeZeroBytes(this)

fun ByteArray.encodeBinary(): ByteArray {
    val lengthBytes = encodeVarInt(this.size)
    val result = ByteArray(lengthBytes.size + this.size)
    System.arraycopy(lengthBytes, 0, result, 0, lengthBytes.size)
    System.arraycopy(this, 0, result, lengthBytes.size, this.size)
    return result
}

fun String.encodeBinary(): ByteArray {
    val utf8Bytes = this.toByteArray(StandardCharsets.UTF_8)
    val lengthBytes = encodeVarInt(utf8Bytes.size)
    val result = ByteArray(lengthBytes.size + utf8Bytes.size)
    System.arraycopy(lengthBytes, 0, result, 0, lengthBytes.size)
    System.arraycopy(utf8Bytes, 0, result, lengthBytes.size, utf8Bytes.size)
    return result
}

fun ByteArray.decodeBinaryString(offset: Int = 0): Pair<String, Int> {
    val (length, consumed) = decodeVarInt(this, offset)
    val string = String(this, offset + consumed, length, StandardCharsets.UTF_8)
    return string to (consumed + length)
}

fun ByteArray.decodeBinaryByteArray(offset: Int = 0): Pair<ByteArray, Int> {
    val (length, consumed) = decodeVarInt(this, offset)
    val result = this.copyOfRange(offset + consumed, offset + consumed + length)
    return result to (consumed + length)
}
