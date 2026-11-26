package sql.planner

import java.math.BigDecimal
import schema.ColumnType
import schema.IndexColumn

/**
 * Turns a parsed literal into the exact JVM type [column]'s key serializer expects (`INT` -> [Int],
 * `SHORT` -> [Short], ...), so it can go into a `Bound`. The parser only ever produces [Long] and [BigDecimal]
 * numbers, but `MultiColumnKeySerializer` casts each key value to its column's own type, so an unconverted literal
 * would fail with a `ClassCastException`.
 *
 * Returns null when the literal **can't be represented exactly** as a key of [column]: the caller must then leave
 * that condition out of the index range and let the residual filter evaluate it (always correct, just not narrowed).
 * Exactness is judged the same way [sql.expr.compareSqlValues] compares, so an index range and the filter never
 * disagree about which rows match:
 * - whole-number columns ([ColumnType.BYTE], [ColumnType.SHORT], [ColumnType.INT], [ColumnType.LONG]): the literal must
 *   be a whole number within the column type's range (`30.0` is fine, `1.5` and `1000` for a BYTE are not).
 * - [ColumnType.FLOAT] / [ColumnType.DOUBLE]: the literal must survive a round trip through the column type
 *   (`0.1` does, `0.10000000000000000001` does not).
 * - [ColumnType.STRING]: the literal must be a string, and the column must use plain byte order - a column with a
 *   [IndexColumn.localeTag] is ordered by collation keys, which `String.compareTo` in the filter doesn't follow.
 * - [ColumnType.BOOLEAN]: the literal must be a boolean.
 * - every other column type (dates, UUID, bytes) has no comparison rules yet, so there is nothing to coerce to.
 *
 * A literal of the wrong kind for the column (a string for an INT) also returns null; the binder rejects those before
 * planning, so reaching that case just means "don't use the index" rather than an error.
 *
 * @param value the literal's non-NULL value; a NULL literal must not reach here (`Bound(null)` means "unbounded").
 */
fun coerceLiteral(value: Any, column: IndexColumn): Any? = when (column.type) {
    ColumnType.BYTE, ColumnType.SHORT, ColumnType.INT, ColumnType.LONG ->
        wholeNumber(value)?.let { narrowTo(column.type, it) }

    ColumnType.DOUBLE ->
        decimal(value)?.let { literal ->
            literal.toDouble().takeIf { it.isFinite() && BigDecimal.valueOf(it).compareTo(literal) == 0 }
        }

    ColumnType.FLOAT ->
        decimal(value)?.let { literal ->
            literal.toFloat().takeIf { it.isFinite() && BigDecimal(it.toString()).compareTo(literal) == 0 }
        }

    ColumnType.STRING -> (value as? String)?.takeIf { column.localeTag == null }

    ColumnType.BOOLEAN -> value as? Boolean

    else -> null
}

/** The literal as a [Long] if it is a whole number that fits one (`30.0` counts), else null. */
private fun wholeNumber(value: Any): Long? = when (value) {
    is Long -> value
    is BigDecimal -> runCatching { value.longValueExact() }.getOrNull()
    else -> null
}

/** [whole] as the JVM type of whole-number column [type], or null if it is outside that type's range. */
private fun narrowTo(type: ColumnType, whole: Long): Any? = when (type) {
    ColumnType.BYTE -> whole.takeIf { it in Byte.MIN_VALUE..Byte.MAX_VALUE }?.toByte()
    ColumnType.SHORT -> whole.takeIf { it in Short.MIN_VALUE..Short.MAX_VALUE }?.toShort()
    ColumnType.INT -> whole.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    else -> whole
}

/** The literal as a [BigDecimal] if it is a number, else null. */
private fun decimal(value: Any): BigDecimal? = when (value) {
    is Long -> BigDecimal.valueOf(value)
    is BigDecimal -> value
    else -> null
}
