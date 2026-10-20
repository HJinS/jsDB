package sql.ast

sealed interface Expr


/** 리터럴. value: Long | BigDecimal | String | Boolean | null (SQL NULL) */
data class Literal(val value: Any?) : Expr

/** 컬럼 참조 (이름은 렉서가 소문자로 접은 값, 따옴표 식별자는 원문 그대로) */
data class ColumnRef(val name: String) : Expr

/** 단항 연산: -x, NOT x */
data class Unary(val op: UnaryOp, val operand: Expr) : Expr

/** 이항 연산: 산술, 비교, AND/OR, || */
data class Binary(val op: BinaryOp, val left: Expr, val right: Expr) : Expr

/** x IS [NOT] NULL */
data class IsNull(val operand: Expr, val negated: Boolean) : Expr

/** x [NOT] IN (a, b, c) */
data class InList(val operand: Expr, val items: List<Expr>, val negated: Boolean) : Expr

/** x [NOT] BETWEEN low AND high */
data class Between(val operand: Expr, val low: Expr, val high: Expr, val negated: Boolean) : Expr

/** x [NOT] LIKE pattern */
data class Like(val operand: Expr, val pattern: Expr, val negated: Boolean) : Expr
