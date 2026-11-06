package sql.expr

import exception.DatabaseException
import java.math.BigDecimal
import schema.ColumnType
import schema.Row
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail

sealed interface ValueExpr {
    /** The value's column type, known at bind time; null for the NULL literal, which fits any type. */
    val type: ColumnType?
    fun eval(row: Row): Any?
}

sealed interface Predicate {
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

data class ColumnExpr(val index: Int, override val type: ColumnType) : ValueExpr {
    override fun eval(row: Row): Any? = row.toList()[index]
}

data class Comparison(val op: CompareOp, val left: ValueExpr, val right: ValueExpr) : Predicate {
    override fun test(row: Row): TriBool {
        val l = left.eval(row) ?: return TriBool.UNKNOWN
        val r = right.eval(row) ?: return TriBool.UNKNOWN
        return if(op.matches(compareSqlValues(l, r))) TriBool.TRUE else TriBool.FALSE
    }
}

data class And(val left: Predicate, val right: Predicate) : Predicate {
    override fun test(row: Row): TriBool = left.test(row) and right.test(row)
}

data class Or(val left: Predicate, val right: Predicate) : Predicate {
    override fun test(row: Row): TriBool = left.test(row) or right.test(row)
}

data class Not(val operand: Predicate) : Predicate {
    override fun test(row: Row): TriBool = !operand.test(row)
}

data class IsNullPredicate(val operand: ValueExpr, val negated: Boolean) : Predicate {
    override fun test(row: Row): TriBool =
        if ((operand.eval(row) == null) != negated) TriBool.TRUE else TriBool.FALSE
}

