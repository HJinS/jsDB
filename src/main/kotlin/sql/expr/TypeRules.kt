package sql.expr

import exception.DatabaseException
import java.math.BigDecimal
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail

/**
 * Three-way comparison of two non-NULL values: negative, zero or positive as [l] is less than, equal to or greater
 * than [r]. Numbers of different classes compare by value (whole numbers as Long, anything fractional as BigDecimal,
 * so `0.1f` equals `0.1`); strings compare lexicographically; false sorts before true.
 *
 * [Binder] rejects incomparable operand types before any row is read, so reaching the `else` means the binder or the
 * stored data is wrong.
 *
 * @throws DatabaseException ([ErrorCode.UNEXPECTED]) if [l] and [r] aren't both numbers, strings or booleans.
 */
fun compareSqlValues(l: Any, r: Any): Int = when {
    l is Number && r is Number -> compareNumbers(l, r)
    l is String && r is String -> l.compareTo(r)
    l is Boolean && r is Boolean -> l.compareTo(r)
    else -> throw DatabaseException(
        ErrorCode.UNEXPECTED,
        SQLErrorDetail(
            reason = "cannot compare ${l::class.simpleName} with ${r::class.simpleName}",
            entityType = EntityType.ROW
        )
    )
}

private fun isIntegral(n: Number) = n is Byte || n is Short || n is Int || n is Long

private fun toBigDecimal(n: Number): BigDecimal = when (n) {
    is BigDecimal -> n
    is Float -> BigDecimal(n.toString())
    is Double -> BigDecimal.valueOf(n)
    else -> BigDecimal.valueOf(n.toLong())
}

private fun compareNumbers(l: Number, r: Number): Int =
    if (isIntegral(l) && isIntegral(r)) l.toLong().compareTo(r.toLong())
    else toBigDecimal(l).compareTo(toBigDecimal(r))

