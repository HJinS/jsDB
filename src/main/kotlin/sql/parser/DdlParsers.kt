package sql.parser

import sql.ast.AddColumn
import sql.ast.ColumnDef
import sql.ast.CreateIndex
import sql.ast.CreateTable
import sql.ast.DropColumn
import sql.ast.DropIndex
import sql.ast.DropTable
import sql.ast.IndexColumnDef
import sql.ast.PrimaryKeyConstraint
import sql.ast.Statement
import sql.ast.TableItem
import sql.lexer.TokenType
import util.requireOrThrow

/** `name type [NOT NULL] [PRIMARY KEY]` - shared by CREATE TABLE and ALTER TABLE ... ADD. */
internal class ColumnDefParser(
    private val cursor: TokenCursor
) {

    fun parse(): ColumnDef {
        val name = cursor.expectIdentifier()
        val typeName = cursor.expectIdentifier()
        var notNull = false
        var primaryKey = false
        while (true) {
            when {
                cursor.match(TokenType.NOT) -> {
                    cursor.expect(TokenType.NULL, "NULL")
                    notNull = true
                }

                cursor.match(TokenType.PRIMARY) -> {
                    cursor.expectWord("key")
                    primaryKey = true
                }

                else -> break
            }
        }
        return ColumnDef(name, typeName, notNull, primaryKey)
    }
}

internal class CreateParser(
    private val cursor: TokenCursor,
    private val columnDefs: ColumnDefParser,
) : StatementParser {

    override fun parse(): Statement {
        cursor.expect(TokenType.CREATE, "CREATE")
        return when {
            cursor.matchWord("table") -> parseTable()
            cursor.matchWord("unique") -> {
                cursor.expectWord("index")
                parseIndex(unique = true)
            }

            cursor.matchWord("index") -> parseIndex(unique = false)
            else -> throw cursor.unexpected("TABLE, INDEX or UNIQUE")
        }
    }

    private fun parseTable(): CreateTable {
        val name = cursor.expectIdentifier()
        return CreateTable(name, cursor.parenthesizedList { parseTableItem() })
    }

    /** Either a column definition or a table-level `[CONSTRAINT name] PRIMARY KEY (a, b)`. */
    private fun parseTableItem(): TableItem {
        val constraintName = if (cursor.match(TokenType.CONSTRAINT)) cursor.expectIdentifier() else null
        if (cursor.match(TokenType.PRIMARY)) {
            cursor.expectWord("key")
            return PrimaryKeyConstraint(cursor.parenthesizedList { cursor.expectIdentifier() }, constraintName)
        }
        requireOrThrow(constraintName == null) { cursor.unexpected("PRIMARY KEY") }
        return columnDefs.parse()
    }

    private fun parseIndex(unique: Boolean): CreateIndex {
        val name = cursor.expectIdentifier()
        cursor.expect(TokenType.ON, "ON")
        val table = cursor.expectIdentifier()
        val columns = cursor.parenthesizedList { IndexColumnDef(cursor.expectIdentifier(), cursor.matchDescending()) }
        return CreateIndex(name, table, columns, unique)
    }
}

internal class DropParser(
    private val cursor: TokenCursor
) : StatementParser {

    override fun parse(): Statement {
        cursor.expect(TokenType.DROP, "DROP")
        return when {
            cursor.matchWord("table") -> DropTable(cursor.expectIdentifier())
            cursor.matchWord("index") -> DropIndex(cursor.expectIdentifier())
            else -> throw cursor.unexpected("TABLE or INDEX")
        }
    }
}

internal class AlterParser(
    private val cursor: TokenCursor,
    private val columnDefs: ColumnDefParser,
) : StatementParser {

    override fun parse(): Statement {
        cursor.expect(TokenType.ALTER, "ALTER")
        cursor.expectWord("table")
        val table = cursor.expectIdentifier()
        return when {
            cursor.matchWord("add") -> {
                cursor.match(TokenType.COLUMN)
                AddColumn(table, columnDefs.parse())
            }

            cursor.match(TokenType.DROP) -> {
                cursor.match(TokenType.COLUMN)
                DropColumn(table, cursor.expectIdentifier())
            }

            else -> throw cursor.unexpected("ADD or DROP")
        }
    }
}
