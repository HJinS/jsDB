package sql.ast

sealed interface SelectItem

data object AllColumns : SelectItem

data class ExprItem(val expr: Expr) : SelectItem

data class OrderItem(val column: String, val descending: Boolean)

data class Select(
    val columns: List<SelectItem>,
    val table: String,
    val where: Expr?,
    val orderBy: List<OrderItem>,
    val limit: Long?,
    val offset: Long?
) : Statement
