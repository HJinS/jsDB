package sql.parser

import sql.ast.Statement

/** Reads one kind of statement, starting at its first token. [Parser] picks the implementation by that token. */
internal interface StatementParser {
    fun parse(): Statement
}
