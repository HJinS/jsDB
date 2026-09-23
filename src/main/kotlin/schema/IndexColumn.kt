package schema

import java.text.Collator
import java.util.Locale

/**
 * One column of an index's key, in key-column order.
 *
 * @property descending This index's own declared sort direction for this column (not a scan
 *   request — see [ColumnOrder.descending] for that).
 * @property localeTag Only meaningful for a [ColumnType.STRING] column: an IETF BCP 47 locale tag
 *   (e.g. `"ko-KR"`) selecting locale-aware collation instead of plain UTF-8 byte order, via
 *   [collation]. Null means plain byte-comparable UTF-8.
 * @property collationStrength Optional `java.text.Collator` strength (e.g. `Collator.PRIMARY`),
 *   applied to [collation] when [localeTag] is set.
 * */
data class IndexColumn(
    val name: String,
    val type: ColumnType,
    val descending: Boolean,
    val localeTag: String? = null,
    val collationStrength: Int? = null
){
    /**
     * Lazily-built [Collator] for [localeTag], or null for plain byte order.
     *
     * **One-way**: a `CollationKey`'s bytes cannot be turned back into the original string, so a
     * key column encoded with this collation only supports byte-comparable *ordering* — decoding
     * it back to a domain value (`KeySerializer.deserialize`) returns a placeholder, not the
     * original string. No current production code path decodes a key this way (rows themselves
     * are read back through `IndexHandle.valueSerializer`, which never uses collation), but a
     * future caller that does (e.g. `BTree.printTree`'s `format`, or a query layer) would see the
     * placeholder instead of real data.
     * */
    val collation: Collator? by lazy {
        localeTag?.let { tag ->
            Collator.getInstance(Locale.forLanguageTag(tag)).apply {
                collationStrength?.let { strength = it }
            }
        }
    }
}
