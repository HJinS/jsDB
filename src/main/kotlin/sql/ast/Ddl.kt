package sql.ast

sealed interface TableItem

data class ColumnDef(
    val name: String,
    val typeName: String,
    val notNull: Boolean,
    val primaryKey: Boolean
) : TableItem

data class PrimaryKeyConstraint(val columns: List<String>, val name: String? = null) : TableItem

data class CreateTable(val name: String, val items: List<TableItem>) : Statement

data class IndexColumnDef(val column: String, val descending: Boolean)

data class CreateIndex(
    val name: String,
    val table: String,
    val columns: List<IndexColumnDef>,
    val unique: Boolean,
) : Statement

data class DropTable(val name: String) : Statement

data class DropIndex(val name: String) : Statement

data class AddColumn(val table: String, val column: ColumnDef) : Statement

data class DropColumn(val table: String, val column: String) : Statement

