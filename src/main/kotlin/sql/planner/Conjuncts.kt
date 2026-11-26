package sql.planner

import sql.expr.And
import sql.expr.ColumnExpr
import sql.expr.CompareOp
import sql.expr.Comparison
import sql.expr.LiteralExpr
import sql.expr.Predicate

/**
 * The AND terms of [p], in their written order, however the ANDs are nested: `And(And(t1, t2), t3)` and
 * `And(t1, And(t2, t3))` both give `[t1, t2, t3]`. Only the top-level ANDs are split; an OR or NOT is one term even if
 * it has ANDs inside (splitting through them would change the meaning).
 */
fun conjuncts(p: Predicate): List<Predicate> = when (p) {
    is And -> conjuncts(p.left) + conjuncts(p.right)
    else -> listOf(p)
}

/**
 * The inverse of [conjuncts]: joins [terms] with AND from the left, `And(And(t1, t2), t3)`. A single term comes back
 * as itself, and an empty list gives null (nothing to check).
 */
fun andAll(terms: List<Predicate>): Predicate? = terms.reduceOrNull { acc, term -> And(acc, term) }

/**
 * One WHERE term in the shape an index can use: `column <op> value`, with the column always on the left.
 *
 * @property value A non-null literal value, as the parser produced it (not yet coerced to the column's type).
 * @property source The term this came from, so the planner can tell which terms an index consumed.
 */
data class ColumnCondition(val column: ColumnExpr, val op: CompareOp, val value: Any, val source: Predicate)

/**
 * This term as a [ColumnCondition], or null if it isn't a comparison between a column and a non-null literal.
 * `5 < a` is turned around into `a > 5`. A NULL literal gives null because `a = NULL` is never TRUE, so no index range
 * can stand for it.
 */
fun Predicate.asColumnCondition(): ColumnCondition? = when (this) {
    is Comparison if left is ColumnExpr && right is LiteralExpr ->
        right.value?.let { ColumnCondition(left, op, it, this) }

    is Comparison if left is LiteralExpr && right is ColumnExpr ->
        left.value?.let { ColumnCondition(right, op.flipped(), it, this) }

    else -> null
}
