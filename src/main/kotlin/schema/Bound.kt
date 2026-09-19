package schema

data class Bound<K>(
    val value: K?,
    val isInclusive: Boolean
)
