package sql.expr

import exception.DatabaseException
import java.math.BigDecimal
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail

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

