package sql.parser

import sql.ast.AllColumns
import sql.ast.ExprItem
import sql.ast.OrderItem
import sql.ast.Select
import sql.ast.SelectItem
import sql.lexer.TokenType

internal class SelectParser(
    private val cursor: TokenCursor,
    private val expressions: ExpressionParser,
) : StatementParser {

    override fun parse(): Select {
        cursor.expect(TokenType.SELECT, "SELECT")
        val columns: List<SelectItem> =
            if (cursor.match(TokenType.STAR)) listOf(AllColumns)
            else cursor.commaSeparated { ExprItem(expressions.parse()) }
        cursor.expect(TokenType.FROM, "FROM")
        val table = cursor.expectIdentifier()
        val where = expressions.parseWhereClause()
        val orderBy = parseOrderBy()
        val limit = if (cursor.match(TokenType.LIMIT)) cursor.expectSignedInteger() else null
        val offset = if (cursor.match(TokenType.OFFSET)) cursor.expectSignedInteger() else null
        return Select(columns, table, where, orderBy, limit, offset)
    }

    private fun parseOrderBy(): List<OrderItem> {
        if (!cursor.match(TokenType.ORDER)) return emptyList()
        cursor.expect(TokenType.BY, "BY")
        return cursor.commaSeparated { OrderItem(cursor.expectIdentifier(), cursor.matchDescending()) }
    }
}
