package sql.ast

data class Insert(
    val table: String,
    val columns: List<String>?,
    val rows: List<List<Expr>>,
) : Statement

data class Assignment(val column: String, val value: Expr)

data class Update(
    val table: String,
    val assignments: List<Assignment>,
    val where: Expr?,
) : Statement

data class Delete(val table: String, val where: Expr?) : Statement
