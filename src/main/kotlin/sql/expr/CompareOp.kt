package sql.expr

import sql.ast.BinaryOp

/** The six SQL comparison operators; a [Comparison] applies one to the sign of a three-way comparison of its operands. */
enum class CompareOp {
    EQ, NE, LT, LE, GT, GE;

    /** Whether this operator holds given [cmp], the negative / zero / positive result of comparing left to right. */
    fun matches(cmp: Int): Boolean = when (this) {
        EQ -> cmp == 0
        NE -> cmp != 0
        LT -> cmp < 0
        LE -> cmp <= 0
        GT -> cmp > 0
        GE -> cmp >= 0
    }

    companion object {
        /** The comparison [op] stands for, or null if [op] isn't a comparison (AND, OR, arithmetic, `||`). */
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

