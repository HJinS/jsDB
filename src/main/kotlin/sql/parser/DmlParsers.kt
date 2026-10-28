package sql.parser

import sql.ast.Assignment
import sql.ast.Delete
import sql.ast.Expr
import sql.ast.Insert
import sql.ast.Update
import sql.lexer.TokenType

internal class InsertParser(
    private val cursor: TokenCursor,
    private val expressions: ExpressionParser,
) : StatementParser {

    override fun parse(): Insert {
        cursor.expect(TokenType.INSERT, "INSERT")
        cursor.expect(TokenType.INTO, "INTO")
        val table = cursor.expectIdentifier()
        val columns =
            if (cursor.current.type == TokenType.LPAREN) cursor.parenthesizedList { cursor.expectIdentifier() }
            else null
        cursor.expect(TokenType.VALUES, "VALUES")
        val rows = cursor.commaSeparated { parseRow() }
        return Insert(table, columns, rows)
    }

    private fun parseRow(): List<Expr> = cursor.parenthesizedList { expressions.parse() }
}

internal class UpdateParser(
    private val cursor: TokenCursor,
    private val expressions: ExpressionParser,
) : StatementParser {

    override fun parse(): Update {
        cursor.expect(TokenType.UPDATE, "UPDATE")
        val table = cursor.expectIdentifier()
        cursor.expect(TokenType.SET, "SET")
        val assignments = cursor.commaSeparated { parseAssignment() }
        return Update(table, assignments, expressions.parseWhereClause())
    }

    private fun parseAssignment(): Assignment {
        val column = cursor.expectIdentifier()
        cursor.expect(TokenType.EQ, "'='")
        return Assignment(column, expressions.parse())
    }
}

internal class DeleteParser(
    private val cursor: TokenCursor,
    private val expressions: ExpressionParser,
) : StatementParser {

    override fun parse(): Delete {
        cursor.expect(TokenType.DELETE, "DELETE")
        cursor.expect(TokenType.FROM, "FROM")
        val table = cursor.expectIdentifier()
        return Delete(table, expressions.parseWhereClause())
    }
}
