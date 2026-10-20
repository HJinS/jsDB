package sql.parser

import exception.SqlSyntaxException
import sql.lexer.Token
import sql.lexer.TokenType
import util.ErrorCode
import util.SyntaxErrorDetail
import util.requireOrThrow


class TokenCursor(private val tokens: List<Token>) {
    var pos = 0
        private set

    var depth = 0
        private set

    operator fun get(ahead: Int = 0) = tokens[minOf(pos + ahead, tokens.lastIndex)]

    val current: Token get() = this[0]

    fun advance(): Token {
        val token = tokens[pos]
        if (pos < tokens.lastIndex) pos++
        return token
    }

    fun match(type: TokenType): Boolean {
        if (current.type != type) return false
        advance()
        return true
    }

    fun expect(type: TokenType, what: String): Token {
        requireOrThrow(current.type == type) { unexpected(what) }
        return advance()
    }

    fun checkIsWord(word: String, ahead: Int = 0): Boolean {
        val token = this[ahead]
        return token.type == TokenType.IDENTIFIER && token.value == word
    }

    fun matchWord(word: String): Boolean {
        if (!checkIsWord(word)) return false
        advance()
        return true
    }

    fun expectWord(word: String): Token {
        requireOrThrow(checkIsWord(word)) { unexpected(word.uppercase()) }
        return advance()
    }

    /** Builds the "expected X but found Y" error for [at] (the current token by default); the caller throws it. */
    fun unexpected(expected: String, at: Token = current) = SqlSyntaxException(
        ErrorCode.UNEXPECTED_TOKEN,
        SyntaxErrorDetail("expected $expected but found ${describe(at)}", at.offset),
    )

    /** Runs [block] one nesting level deeper, so recursion on hostile input fails cleanly instead of overflowing the stack. */
    fun <T> nested(block: () -> T): T {
        enter()
        val result = block()
        leave()
        return result
    }

    private fun enter() {
        depth++
        requireOrThrow(depth <= MAX_DEPTH) {
            SqlSyntaxException(
                ErrorCode.NESTING_TOO_DEEP,
                SyntaxErrorDetail("expression is nested too deeply (limit $MAX_DEPTH)", current.offset),
            )
        }
    }

    private fun leave() {
        depth--
    }

    private fun describe(token: Token) = if (token.type == TokenType.EOF) "end of input" else "'${token.text}'"

    private companion object {
        const val MAX_DEPTH = 200
    }
}
