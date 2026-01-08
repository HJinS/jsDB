package index.comparator

import index.util.KeySchema
import java.util.Arrays


class MultiColumnKeyComparator(private val schema: KeySchema): KeyComparator {
    override fun compare(key1: ByteArray, key2: ByteArray): Int {
        val compareResult = Arrays.compareUnsigned(key1, key2)
        return if(compareResult > 0) 1 else if(compareResult < 0) -1 else 0
    }
}