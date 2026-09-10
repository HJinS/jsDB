package util

/**
 * 사전식 정렬을 위한 바이트 배열 비교 (unsigned)
 * 0xFF: 0b11111111
 * */
fun ByteArray.compareTo(other: ByteArray, descending: Boolean=false): Int {
    val minLen = minOf(this.size, other.size)
    for (i in 0 until minLen) {
        val a = this[i].toInt() and 0xFF
        val b = other[i].toInt() and 0xFF
        if (a != b){
            return if(a < b){
                if (descending) 1 else -1
            }else{
                if (descending) -1 else 1
            }
        }
    }
    return if(!descending) this.size.compareTo(other.size) else -this.size.compareTo(other.size)
}
