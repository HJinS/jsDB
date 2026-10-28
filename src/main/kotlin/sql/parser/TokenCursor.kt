package sql.parser

import exception.SqlSyntaxException
import sql.lexer.Token
import sql.lexer.TokenType
import util.ErrorCode
import util.SyntaxErrorDetail
import util.requireOrThrow

/**
 * A position over the token list, plus the small reading rules every grammar rule shares
 * (identifiers, integers, comma lists, parentheses). Grammar rules sit on top of this and only
 * decide *which* of these to read in what order.
 */
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

    /** Words that are keywords only in some grammar positions are plain identifiers to the lexer; [word] is lowercase. */
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

    fun expectIdentifier(): String {
        requireOrThrow(current.type in NAME_TYPES) { unexpected("identifier") }
        return advance().value as String
    }

    fun expectInteger(): Long = expect(TokenType.INTEGER, "integer").value as Long

    /** An integer with an optional leading minus; a negative value is the caller's (the engine's) to judge. */
    fun expectSignedInteger(): Long {
        val negative = match(TokenType.MINUS)
        val value = expectInteger()
        return if (negative) -value else value
    }

    /** `item (, item)*` - [parseOne] must consume tokens or throw, never return without progress. */
    fun <T> commaSeparated(parseOne: () -> T): List<T> = buildList {
        add(parseOne())
        while (match(TokenType.COMMA)) add(parseOne())
    }

    /** `( inner )` */
    fun <T> parenthesized(parseInner: () -> T): T {
        expect(TokenType.LPAREN, "'('")
        val inner = parseInner()
        expect(TokenType.RPAREN, "')'")
        return inner
    }

    /** `( item (, item)* )` */
    fun <T> parenthesizedList(parseOne: () -> T): List<T> = parenthesized { commaSeparated(parseOne) }

    /** Optional trailing sort direction: true for DESC, false for ASC or none. */
    fun matchDescending(): Boolean {
        if (matchWord("desc")) return true
        matchWord("asc")
        return false
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
        val NAME_TYPES = setOf(TokenType.IDENTIFIER, TokenType.QUOTED_IDENTIFIER)
    }
}
