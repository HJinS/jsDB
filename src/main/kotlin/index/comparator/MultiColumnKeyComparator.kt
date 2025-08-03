package index.comparator

import index.util.KeySchema
import index.util.comparePackedItem


class MultiColumnKeyComparator(private val schema: KeySchema): KeyComparator {
    override fun compare(key1: ByteArray, key2: ByteArray): Int {
        var offset1 = 0
        var offset2 = 0
        for (column in schema.columns){
            val byte1 = key1[offset1]
            val byte2 = key2.getOrElse(offset2) { _ -> 0x00.toByte()}

            val isNull1 = byte1 == 0x00.toByte()
            val isNull2 = byte2 == 0x00.toByte()
            if (isNull1 && isNull2){
                offset1++
                offset2++
                continue
            }
            if (isNull1) return if (column.descending) 1 else -1
            if (isNull2) return if (column.descending) -1 else 1
            offset1++
            offset2++

            val (cmp, consumed1, consumed2) = comparePackedItem(key1, offset1, key2, offset2, column)
            if (cmp != 0) return cmp
            offset1 += consumed1
            offset2 += consumed2
        }
        return 0
    }

}