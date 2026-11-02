package sql.expr

import schema.ColumnType
import schema.Row

sealed interface ValueExpr {
    fun eval(row: Row): Any?
}

sealed interface Predicate {
    fun test(row: Row): TriBool
}

data class LiteralExpr(val value: Any?) : ValueExpr {
    override fun eval(row: Row): Any? = value
}

data class ColumnExpr(val index: Int, val type: ColumnType) : ValueExpr {
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

