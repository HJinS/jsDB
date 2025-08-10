package index.util

import java.nio.ByteBuffer
import kotlin.experimental.xor


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
 * 0xFF: 0b11111111
 * 0x80: 0b10000000
 * 0x7F: 0b01111111
 *
 * example
 *  - original value: 10000000 11010100 10101010 00000101
 *  - first: 0000000
 *  - second: 1010100 0000000
 *  - third: 0101010 1010100 0000000
 *  - fourth: 0000101 0101010 1010100 0000000
 *  - fifth: 10101010 10101010 00000000
 * */
fun decodeVarInt(bytes: ByteArray, offset: Int = 0, descending: Boolean = false): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var pos = offset

    while (pos < bytes.size) {
        var byte = bytes[pos].toInt()
        byte = if (descending) {
            byte xor 0xFF // invert 처리
        } else {
            byte and 0xFF // ByteArray 에서 디코딩 대상 byte 가져옴
        }
        // 뒤 7bit를 가지교 와서 7칸 쉬프트(처음엔 0칸)한 후에 or로 저장
        // 인코딩 할 때 높은 자리 숫자가 배열 뒤쪽으로 오게 됨
        // 숫자 가장 앞의 bit가 1이면 뒤에 숫자가 더 있다는 뜻
        result = result or ((byte and 0x7F) shl shift)
        pos++
        if ((byte and 0x80) == 0) break
        shift += 7
    }
    return result to (pos - offset)
}

/**
 * 인덱스는 기본적으로 오름차순으로 정렬됨. 이를 DESC 로 정렬하기 위해 저장 할 떄의 Byte 순서를 반전(invert) 해서 저장
 * 0xFF = 0000 0000 0000 0000 0000 0000 1111 1111
 * */
fun ByteArray.invert(descending: Boolean): ByteArray{
    return if (descending) this.map { (it.toInt() xor 0xFF).toByte() }.toByteArray() else this
}

/**
 * 가장 앞의 부호비트를 뒤집어서 정렬가능한 표현으로 변환
 * asc, desc 의 경우는 cmp 결과에 -1 곱하는 방식으로 사용
 *  - invert 할 경우 결과가 깨질 수 있음
 * */
fun Int.encodeSortable(): ByteArray{
    val sortableBits = this xor Int.MIN_VALUE
    return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(sortableBits).array()
}

fun Long.encodeSortable(): ByteArray{
    val sortableBits = this xor Long.MIN_VALUE
    return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(sortableBits).array()
}

fun Short.encodeSortable(): ByteArray{
    val sortableBits = this xor Short.MIN_VALUE
    return ByteBuffer.allocate(Short.SIZE_BYTES).putShort(sortableBits).array()
}

/**
 * Bit 표현을 가져와서 양수인 경우 최상위 비트를 1로 변경. 음수인 경우 모든 bit를 뒤집음 이를 packing
 * asc, desc 의 경우는 cmp 결과에 -1 곱하는 방식으로 사용
 *  - invert 할 경우 결과가 깨질 수 있음
 * */

fun Float.encodeSortable(): ByteArray{
    val bits = this.toRawBits()
    val sortableBits = if(this > 0f) bits xor Int.MIN_VALUE else bits.inv()
    return ByteBuffer.allocate(4).putInt(sortableBits).array()
}

fun Double.encodeSortable(): ByteArray{
    val bits = this.toRawBits()
    val sortableBits = if(this > 0.0) bits xor Long.MIN_VALUE else bits.inv()
    return ByteBuffer.allocate(8).putLong(sortableBits).array()
}

fun ByteArray.decodeSortableInt(): Int{
    val sortableBits = ByteBuffer.wrap(this).int
    return sortableBits xor Int.MIN_VALUE
}

fun ByteArray.decodeSortableLong(): Long{
    val sortableBits = ByteBuffer.wrap(this).long
    return sortableBits xor Long.MIN_VALUE
}

fun ByteArray.decodeSortableShort(): Short{
    val sortableBits = ByteBuffer.wrap(this).short
    return sortableBits xor Short.MIN_VALUE
}

/**
 * 인코딩 규칙을 역으로 적용
 * 양수인 경우에 MSB bit 를 1로 바꾸었기 때문에 0보다 작으면 xor 을 통해 원래대로 되돌림.
 * */
fun ByteArray.decodeSortableFloat(): Float{
    val sortableBits = ByteBuffer.wrap(this).int
    val originalBits = if(sortableBits < 0) sortableBits xor Int.MIN_VALUE else sortableBits.inv()
    return Float.fromBits(originalBits)
}

fun ByteArray.decodeSortableDouble(): Double{
    val sortableBits = ByteBuffer.wrap(this).long
    val originalBits = if(sortableBits < 0) sortableBits xor Long.MIN_VALUE else sortableBits.inv()
    return Double.fromBits(originalBits)
}