package index.util


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
 * * example)
 *  * original value: 10000000 11010100 10101010 00000101
 *  * first: 0000000
 *  * second: 1010100 0000000
 *  * third: 0101010 1010100 0000000
 *  * fourth: 0000101 0101010 1010100 0000000
 *  * fifth: 10101010 10101010 00000000
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
