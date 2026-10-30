package sql

import sql.ast.Statement
import sql.lexer.Lexer
import sql.parser.Parser

object SqlParser {
    fun parse(sql: String): Statement = Parser(Lexer(sql).tokenize()).parseStatement()
}
