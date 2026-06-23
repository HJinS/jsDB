package index.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.UUID
import kotlin.experimental.inv
import kotlin.experimental.xor
import kotlin.text.toByteArray
import index.exception.SerializerException


/**
 * 작은 수는 작은 공간에, 큰 수는 큰 공간에 저장하기 위함(7 조각씩 자름)
 * 1 -> 0x01
 * 300 -> 0xAC 0x02
 * Unsigned LEB128 기반
 * ushr: bit 오른쪽을 이동
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
        v = v ushr 7 // 7비트 옮겨서 다음 자리의 숫자 확인
        if (v != 0) b = b or 0x80 // 0이 아닌 경우 앞에 가장 앞 자리 숫자를(MSB) 1로 변경
        output.add(b.toByte()) // 원래 숫자를 추가
    } while (v != 0)
    return output.toByteArray()
}

/**
 * encode 역함수
 * shl: bit 값을 왼쪽으로 이동
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
 * 00000101
 * */
fun decodeVarInt(bytes: ByteArray, offset: Int = 0): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var pos = offset

    while (true) {
        // 배열 범위를 벗어나는지 확인
        if (pos >= bytes.size) {
            throw SerializerException.DecodeException.PositionOutOfBoundsException(pos, bytes.size)
        }

        val byte = bytes[pos].toInt() and 0xFF
        // ByteArray 에서 디코딩 대상 byte 가져옴 + 부호 없는 정수로 변환(int 변환 시 부호 확장을 고려하여 마지막 8bit만 가져옴)
        // 뒤 7bit를 가지교 와서 7칸 쉬프트(처음엔 0칸)한 후에 or로 저장
        // 인코딩 할 때 높은 자리 숫자가 배열 뒤쪽으로 오게 됨
        // 숫자 가장 앞의 bit가 1이면 뒤에 숫자가 더 있다는 뜻
        result = result or ((byte and 0x7F) shl shift)
        pos++

        // MSB가 0이면 마지막 바이트 이므로 종료
        if ((byte and 0x80) == 0) break
        shift += 7

        // 32비트 Int를 넘어서는 과도한 데이터 방지
        if (shift >= 32) {
            throw SerializerException.DecodeException.VarIntTooLongException()
        }
    }
    return result to (pos - offset)
}

/**
 * 인덱스는 기본적으로 오름차순으로 정렬됨. 이를 DESC 로 정렬하기 위해 저장 할 떄의 Byte 순서를 반전(invert) 해서 저장
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
 * 가장 앞의 부호비트를 뒤집어서 정렬가능한 표현으로 변환
 * asc, desc 의 경우는 cmp 결과에 -1 곱하는 방식으로 사용
 *  - invert 할 경우 결과가 깨질 수 있음
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

fun UUID.encodeSortable(): ByteArray{
    val bytes = ByteBuffer.allocate(16)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits)
        .array()
    return escapeZeroBytes(bytes)
}

/**
 * Bit 표현을 가져와서 양수인 경우 최상위 비트를 1로 변경. 음수인 경우 모든 bit를 뒤집음 이를 packing
 * asc, desc 의 경우는 cmp 결과에 -1 곱하는 방식으로 사용
 *  - invert 할 경우 결과가 깨질 수 있음
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
 * 0x00을 0x00 0xFF 로 escape 처리 + Termination mark 추가
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
 * string encode 할 경우 바로 사전식 비교를 할 수 있도록 길이 정보를 encoding 하는 것이 아닌 terminator(0x00) 을 끝에 붙이는 방식 사용
 * 문자열 중간에 0x00 이 있는 경우에는 0x00 을 0x00 0xFF 로 Escape 처리 하여 사용
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
 * 인코딩 규칙을 역으로 적용
 * 양수인 경우에 MSB bit 를 1로 바꾸었기 때문에 0보다 작으면 xor 을 통해 원래대로 되돌림.
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


fun ByteArray.decodeSortableUUID(): UUID{
    val unEscaped = unescapeZeroBytes(this)
    if(unEscaped.size != 16) throw SerializerException.DecodeException.InvalidUUIDLengthException(unEscaped.size)

    val byteBuffer = ByteBuffer.wrap(unEscaped).order(ByteOrder.BIG_ENDIAN)
    return UUID(byteBuffer.long, byteBuffer.long)
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

fun ByteArray.decodeSortableString(collator: Collator?): String{
    val unescaped = unescapeZeroBytes(this)
    return if (collator != null){
        "[CollationKey(${unescaped.joinToString(""){ "%02x".format(it) }})]"
    } else{
        unescaped.toString(StandardCharsets.UTF_8)
    }
}

fun ByteArray.decodeSortableByteArray() = unescapeZeroBytes(this)
