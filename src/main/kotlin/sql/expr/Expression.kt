package sql.expr

import exception.DatabaseException
import java.math.BigDecimal
import schema.ColumnType
import schema.Row
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail

/**
 * A bound expression that produces a value from a row: a literal or a column. Built by [Binder], which has already
 * resolved column names to positions and checked types, so evaluating it does no lookups or validation.
 */
sealed interface ValueExpr {
    /** The value's column type, known at bind time; null for the NULL literal, which fits any type. */
    val type: ColumnType?

    /** This expression's value for [row]; null is SQL NULL. */
    fun eval(row: Row): Any?
}

/** A bound condition: judges a row [TriBool.TRUE], [TriBool.FALSE] or [TriBool.UNKNOWN] (when NULL is involved). */
sealed interface Predicate {
    /** This condition's result for [row]. Pass it to a WHERE filter as `test(row).isTrue`. */
    fun test(row: Row): TriBool
}

/** [value] is what the parser produces: Long | BigDecimal | String | Boolean | null. */
data class LiteralExpr(val value: Any?) : ValueExpr {
    override val type: ColumnType?
        get() = when (value) {
            null -> null
            is Long -> ColumnType.LONG
            is BigDecimal -> ColumnType.DOUBLE
            is String -> ColumnType.STRING
            is Boolean -> ColumnType.BOOLEAN
            else -> throw DatabaseException(
                ErrorCode.UNEXPECTED,
                SQLErrorDetail(reason = "unexpected literal ${value::class.simpleName}", entityType = EntityType.EXPRESSION)
            )
        }

    override fun eval(row: Row): Any? = value
}

/** A column, as its [index] in the row's schema and its [type]. */
data class ColumnExpr(val index: Int, override val type: ColumnType) : ValueExpr {
    override fun eval(row: Row): Any? = row.toList()[index]
}

/** `left <op> right`. UNKNOWN if either side is NULL (even `NULL = NULL`); otherwise TRUE or FALSE. */
data class Comparison(val op: CompareOp, val left: ValueExpr, val right: ValueExpr) : Predicate {
    override fun test(row: Row): TriBool {
        val l = left.eval(row) ?: return TriBool.UNKNOWN
        val r = right.eval(row) ?: return TriBool.UNKNOWN
        return if(op.matches(compareSqlValues(l, r))) TriBool.TRUE else TriBool.FALSE
    }
}

/** [left] AND [right], per [TriBool.and]. */
data class And(val left: Predicate, val right: Predicate) : Predicate {
    override fun test(row: Row): TriBool = left.test(row) and right.test(row)
}

/** [left] OR [right], per [TriBool.or]. */
data class Or(val left: Predicate, val right: Predicate) : Predicate {
    override fun test(row: Row): TriBool = left.test(row) or right.test(row)
}

/** NOT [operand]; an UNKNOWN operand stays UNKNOWN. */
data class Not(val operand: Predicate) : Predicate {
    override fun test(row: Row): TriBool = !operand.test(row)
}

/** `operand IS NULL`, or `IS NOT NULL` when [negated]. The one test that sees NULL: it is only ever TRUE or FALSE. */
data class IsNullPredicate(val operand: ValueExpr, val negated: Boolean) : Predicate {
    override fun test(row: Row): TriBool =
        if ((operand.eval(row) == null) != negated) TriBool.TRUE else TriBool.FALSE
}

