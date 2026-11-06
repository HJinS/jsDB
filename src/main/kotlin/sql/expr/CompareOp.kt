package sql.expr

import sql.ast.BinaryOp

enum class CompareOp {
    EQ, NE, LT, LE, GT, GE;

    fun matches(cmp: Int): Boolean = when (this) {
        EQ -> cmp == 0
        NE -> cmp != 0
        LT -> cmp < 0
        LE -> cmp <= 0
        GT -> cmp > 0
        GE -> cmp >= 0
    }

    companion object {
        fun from(op: BinaryOp): CompareOp? = when (op) {
            BinaryOp.EQ -> EQ
            BinaryOp.NE -> NE
            BinaryOp.LT -> LT
            BinaryOp.LE -> LE
            BinaryOp.GT -> GT
            BinaryOp.GE -> GE
            else -> null
        }
    }
}

