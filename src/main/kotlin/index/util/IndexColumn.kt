package index.util

import java.text.Collator
import java.util.Locale

data class IndexColumn(
    val name: String,
    val type: ColumnType,
    val descending: Boolean,
    val localeTag: String? = null,
    val collationStrength: Int? = null,
){
    val collation: Collator? by lazy {
        localeTag?.let { tag ->
            Collator.getInstance(Locale.forLanguageTag(tag)).apply {
                collationStrength?.let { strength = it }
            }
        }
    }
}
