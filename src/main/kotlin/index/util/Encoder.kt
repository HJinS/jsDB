package index.util


/**
 * 작은 수는 작은 공강네, 큰 수는 큰 공간에 저장하기 위함
 * 1 -> 0x01
 * 300 -> 0xAC 0x02
 * Unsigned LEB128 기반
 * ushr: bit 오른쪽을 이동
 * */
fun encodeVarInt(value: Int): ByteArray{
    var v = value
    val output = mutableListOf<Byte>()
    do {
        var b = (v and 0x7F)
        v = v ushr 7
        if (v != 0) b = b or  0x80
        output.add(b.toByte())
    } while (v != 0)
    return output.toByteArray()
}

/**
 * encode 역함수
 * shl: bit 값을 왼쪽으로 이동
 * */
fun decodeVarInt(bytes: ByteArray, offset: Int = 0, descending: Boolean = false): Pair<Int, Int> {
    var result = 0
    var shift = 0
    var pos = offset

    while (true) {
        var byte = bytes[pos]
        if (descending){
            byte = (byte.toInt() xor 0xFF).toByte()
        }
        result = result or ((byte.toInt() and 0x7F) shl shift)
        shift += 7
        pos++
        if ((byte.toInt() and 0x80) == 0) break
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
