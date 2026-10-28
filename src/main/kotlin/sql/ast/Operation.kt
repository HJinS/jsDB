package sql.ast

enum class UnaryOp { NOT, NEG }

enum class BinaryOp {
    OR, AND,                          // 논리
    EQ, NE, LT, LE, GT, GE,           // 비교
    ADD, SUB, MUL, DIV, MOD,          // 산술
    CONCAT,                           // ||
}
