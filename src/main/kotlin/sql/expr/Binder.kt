package sql.expr

import exception.DatabaseException
import schema.ColumnType
import schema.RowSchema
import sql.ast.Between
import sql.ast.Binary
import sql.ast.BinaryOp
import sql.ast.ColumnRef
import sql.ast.Expr
import sql.ast.InList
import sql.ast.IsNull
import sql.ast.Like
import sql.ast.Literal
import sql.ast.Unary
import sql.ast.UnaryOp
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail
import util.requireOrThrow

/** Binds [expr] (a WHERE-style condition) against [schema]. */
fun bind(expr: Expr, schema: RowSchema): Predicate = Binder(schema).bind(expr)

/**
 * Turns a parsed [Expr] into a [Predicate] that can be evaluated against rows of [schema]: column names become
 * positions, and column existence and operand types are checked once, here, rather than per row.
 *
 * @throws DatabaseException [ErrorCode.UNDEFINED_COLUMN] for an unknown column, [ErrorCode.DATATYPE_MISMATCH] for
 * incomparable operands or a value where a condition belongs, [ErrorCode.FEATURE_NOT_SUPPORTED] for LIKE and arithmetic.
 */
class Binder(private val schema: RowSchema) {

    fun bind(expr: Expr): Predicate = predicate(expr)

    /** [expr] sits where a condition is expected. */
    private fun predicate(expr: Expr): Predicate = when (expr) {
        is Binary -> {
            val (op, l, r) = expr
            when (op) {
                BinaryOp.AND -> And(predicate(l), predicate(r))
                BinaryOp.OR -> Or(predicate(l), predicate(r))
                in listOf(BinaryOp.EQ, BinaryOp.NE, BinaryOp.LT, BinaryOp.LE, BinaryOp.GT, BinaryOp.GE) ->
                    compare(CompareOp.from(op)!!, value(l), value(r))

                else -> throw unsupported("arithmetic and concatenation")
            }
        }

        is Unary -> when (expr.op) {
            UnaryOp.NOT -> Not(predicate(expr.operand))
            UnaryOp.NEG -> throw unsupported("unary minus")
        }

        is IsNull -> IsNullPredicate(value(expr.operand), expr.negated)

        is Between -> {
            val (operand, low, high, negated) = expr
            val x = value(operand)
            val inRange = And(
                compare(CompareOp.GE, x, value(low)),
                compare(CompareOp.LE, x, value(high))
            )
            if (negated) Not(inRange) else inRange
        }

        is InList -> {
            val (operand, items, negated) = expr
            val x = value(operand)
            val anyMatch = items
                .map { compare(CompareOp.EQ, x, value(it)) }
                .reduce { acc, p -> Or(acc, p) }
            if (negated) Not(anyMatch) else anyMatch
        }

        is Like -> throw unsupported("LIKE")

        is Literal, is ColumnRef -> throw mismatch("expected a condition but found a value")
    }

    /** [expr] sits where a value is expected. */
    private fun value(expr: Expr): ValueExpr = when (expr) {
        is Literal -> LiteralExpr(expr.value)
        is ColumnRef -> {
            val idx = schema.columnIndex(expr.name)
            ColumnExpr(idx, schema.rowColumns[idx].type)
        }

        is Unary -> throw if (expr.op == UnaryOp.NEG) unsupported("unary minus") else mismatch("expected a value but found a condition")

        is Binary -> {
            val isCondition = expr.op == BinaryOp.AND || expr.op == BinaryOp.OR || CompareOp.from(expr.op) != null
            throw if (isCondition) mismatch("expected a value but found a condition") else unsupported("arithmetic and concatenation")
        }

        is Like -> throw unsupported("LIKE")

        is IsNull, is Between, is InList -> throw mismatch("expected a value but found a condition")
    }

    private fun compare(op: CompareOp, l: ValueExpr, r: ValueExpr): Predicate {
        checkComparable(l, r)
        return Comparison(op, l, r)
    }

    /**
     * Two operands are comparable when they fall in the same [TypeGroup], or are both numeric (an integer column
     * against a decimal literal, say). A NULL literal has no type and fits anything. Everything else - including
     * dates, UUIDs and bytes, which have no group yet - is rejected.
     */
    private fun checkComparable(l: ValueExpr, r: ValueExpr) {
        val lt = l.type ?: return
        val rt = r.type ?: return
        val lg = groupOf(lt)
        val rg = groupOf(rt)
        val comparable = lg != null && rg != null && (lg == rg || (lg.numeric && rg.numeric))
        requireOrThrow(comparable) { mismatch("cannot compare $lt with $rt") }
    }

    private fun groupOf(type: ColumnType): TypeGroup? = when (type) {
        ColumnType.BYTE, ColumnType.SHORT, ColumnType.INT, ColumnType.LONG -> TypeGroup.INTEGER
        ColumnType.FLOAT, ColumnType.DOUBLE -> TypeGroup.DECIMAL
        ColumnType.STRING -> TypeGroup.TEXT
        ColumnType.BOOLEAN -> TypeGroup.BOOL
        else -> null
    }

    private enum class TypeGroup(val numeric: Boolean) {
        INTEGER(true), DECIMAL(true), TEXT(false), BOOL(false)
    }

    private fun mismatch(reason: String) =
        DatabaseException(ErrorCode.DATATYPE_MISMATCH, SQLErrorDetail(reason, entityType = EntityType.EXPRESSION))

    private fun unsupported(what: String) =
        DatabaseException(
            ErrorCode.FEATURE_NOT_SUPPORTED,
            SQLErrorDetail("$what is not supported", entityType = EntityType.EXPRESSION)
        )
}
