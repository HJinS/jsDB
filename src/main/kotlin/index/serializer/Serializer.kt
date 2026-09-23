package index.serializer

/**
 * Converts a domain key of type [K] to/from the byte-comparable `ByteArray` a [index.btree.BTree]
 * actually stores. [index.btree.BTree] itself never calls these — the caller (`database.Table`,
 * `catalog.CatalogManager`) owns serialization entirely; see [index.btree.BTree]'s class doc.
 * */
interface KeySerializer<K> {
    /** Full, exact encoding of [key] — byte-comparable, so `a.compareTo(b)` on the domain values
     * matches unsigned-byte comparison of `serialize(a)` vs `serialize(b)`. */
    fun serialize(key: K): ByteArray

    /**
     * Inverse of [serialize]. Not guaranteed to round-trip for bytes this didn't produce — e.g. a
     * padded prefix key from a multi-column implementation may decode to fewer columns than
     * expected (see [MultiColumnKeySerializer.deserialize]).
     * */
    fun deserialize(bytes: ByteArray): K

    /** Debug-only human-readable rendering of [key], independent of the byte encoding. */
    fun format(key: K): String

    /**
     * The successor of [serialize]\([key]\): the smallest byte sequence that's strictly greater
     * than every byte sequence [serialize] could produce for [key] or anything sharing [key] as a
     * prefix. Used to express an *inclusive* upper bound / *exclusive* lower bound as a single
     * fixed `<`/`>=` comparator (`x <= v` ⟺ `x < successor(v)`) — see
     * `docs/index/range-scan-design.md`.
     *
     * Returns null only when no successor exists (the bytes are already the maximum possible —
     * see [MultiColumnKeySerializer.serializeUpper] for the one case this arises in practice).
     * */
    fun serializeUpper(key: K): ByteArray?
}

/**
 * Converts a domain value of type [V] to/from the `ByteArray` a [index.btree.BTree] stores
 * alongside a key. Unlike [KeySerializer], value bytes are never compared — only stored and read
 * back — so there's no byte-comparability requirement here.
 * */
interface ValueSerializer<V> {
    fun serialize(value: V): ByteArray

    /** Inverse of [serialize]. @return The decoded value, plus how many bytes of [bytes] it consumed. */
    fun deserialize(bytes: ByteArray): Pair<V, Int>
}
