package sql.parser

import sql.ast.Statement
import sql.lexer.Token
import sql.lexer.TokenType

/**
 * Entry point: turns a token list into one [Statement]. It only decides which [StatementParser]
 * handles the statement (by its first token) and checks that nothing is left over afterwards; the
 * grammar itself lives in the statement parsers and [ExpressionParser].
 */
class Parser(tokens: List<Token>) {
    private val cursor = TokenCursor(tokens)
    private val expressions = ExpressionParser(cursor)
    private val columnDefs = ColumnDefParser(cursor)

    private val statementParsers: Map<TokenType, StatementParser> = mapOf(
        TokenType.SELECT to SelectParser(cursor, expressions),
        TokenType.INSERT to InsertParser(cursor, expressions),
        TokenType.UPDATE to UpdateParser(cursor, expressions),
        TokenType.DELETE to DeleteParser(cursor, expressions),
        TokenType.CREATE to CreateParser(cursor, columnDefs),
        TokenType.DROP to DropParser(cursor),
        TokenType.ALTER to AlterParser(cursor, columnDefs),
    )

    fun parseStatement(): Statement {
        val parser = statementParsers[cursor.current.type] ?: throw cursor.unexpected("a statement")
        val statement = parser.parse()
        cursor.match(TokenType.SEMICOLON)
        cursor.expect(TokenType.EOF, "end of input")
        return statement
    }
}
