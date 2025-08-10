package index.comparator

interface KeyComparator {
    fun compare(key1: ByteArray, key2: ByteArray): Int
}

