package index.comparator

import index.util.KeySchema


class MultiColumnKeyComparator(private val schema: KeySchema): KeyComparator {
    override fun compare(key1: ByteArray, key2: ByteArray): Int {
        val key1Length = key1.size
        val key2Length = key2.size
        val len = minOf(key1Length, key2Length)

        for(idx in 0 until len){
            val byteKey1 = key1[idx].toInt() and 0xFF
            val byteKey2 = key2[idx].toInt() and 0xFF

            if(byteKey1 != byteKey2){
                return if(byteKey1 > byteKey2) 1 else -1
            }
        }
        return if(key1Length > key2Length) 1 else if(key1Length < key2Length) -1 else 0
    }
}