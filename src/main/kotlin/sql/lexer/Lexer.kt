package sql.lexer

import exception.SqlSyntaxException
import util.ErrorCode
import util.SyntaxErrorDetail
import util.requireOrThrow
import java.math.BigDecimal

class Lexer(input: String) {
    private val cursor = CharCursor(input)
    private val tokens = mutableListOf<Token>()

    fun tokenize(): List<Token> {
        while (true) {
            skipWhitespaceAndComments()
            if (cursor.atEnd) {
                emit(TokenType.EOF, cursor.pos)
                return tokens.toList()
            }
            val char = cursor.current
            var startPos = cursor.pos
            when {
                isIdentStart(char) -> scanWord(startPos)
                char.isDigit() || (char == '.' && cursor[1].isDigit()) -> scanNumber(startPos)
                char == '\'' -> scanString(startPos)
                char == '"' -> scanQuotedIdentifier(startPos)
                else -> scanOperator(startPos)
            }

        }
    }

    private fun emit(type: TokenType, start: Int, value: Any? = null) {
        tokens += Token(type, cursor.slice(start), start, value)
    }

    private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'

    private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun skipWhitespaceAndComments() {
        outer@ while (true) {
            val char = cursor.current
            if (char == ' ' || char == '\t' || char == '\n' || char == '\r') {
                cursor.advance()
                continue
            }
            if (char == '-' && cursor[1] == '-') {
                cursor.advance(2)
                while (!cursor.atEnd && cursor.current != '\n') cursor.advance()
                continue
            }
            if (char == '/' && cursor[1] == '*') {
                val commentStart = cursor.pos
                cursor.advance(2)
                inner@ while (true) {
                    requireOrThrow(!cursor.atEnd) {
                        SqlSyntaxException(
                            ErrorCode.UNTERMINATED_COMMENT,
                            SyntaxErrorDetail("unterminated /* comment", commentStart),
                        )
                    }
                    if (cursor.current == '*' && cursor[1] == '/') {
                        cursor.advance(2)
                        break@inner
                    }
                    cursor.advance()
                }
                continue
            }
            break

        }
    }

    private fun scanWord(start: Int) {
        while (isIdentPart(cursor.current)) cursor.advance()
        val text = cursor.slice(start)
        val type = TokenType.keywordOf(text)
        if (type != null) emit(type, start)
        else emit(TokenType.IDENTIFIER, start, text.lowercase())
    }

    private fun scanNumber(start: Int) {
        var isDecimal = false
        if (cursor.current == '.') {
            isDecimal = true
            cursor.advance()
            while (cursor.current.isDigit()) cursor.advance()
        } else {
            while (cursor.current.isDigit()) cursor.advance()
            if (cursor.current == '.' && cursor[1].isDigit()) {
                isDecimal = true
                cursor.advance()
                while (cursor.current.isDigit()) cursor.advance()
            }
        }
        if ((cursor.current == 'e' || cursor.current == 'E') && (cursor[1].isDigit() || (cursor[1] in "+-" && cursor[2].isDigit()))) {
            isDecimal = true
            cursor.advance(if (cursor[1] in "+-") 2 else 1)
            while (cursor.current.isDigit()) cursor.advance()
        }
        requireOrThrow(!isIdentStart(cursor.current)) {
            SqlSyntaxException(
                ErrorCode.INVALID_NUMBER,
                SyntaxErrorDetail("trailing characters after numeric literal '${cursor.slice(start)}'", start),
            )
        }
        val text = cursor.slice(start)
        if (isDecimal) emit(TokenType.DECIMAL, start, BigDecimal(text))
        else emit(
            TokenType.INTEGER, start,
            text.toLongOrNull() ?: throw SqlSyntaxException(
                ErrorCode.NUMBER_OUT_OF_RANGE,
                SyntaxErrorDetail("integer literal '$text' is out of range", start),
            ),
        )
    }

    private fun scanQuotedIdentifier(start: Int) {
        scanQuoted(start, '"', TokenType.QUOTED_IDENTIFIER, ErrorCode.UNTERMINATED_QUOTED_IDENTIFIER)
    }

    private fun scanString(start: Int) {
        scanQuoted(start, '\'', TokenType.STRING, ErrorCode.UNTERMINATED_STRING)
    }

    private fun scanQuoted(start: Int, quote: Char, type: TokenType, errorCode: ErrorCode) {
        cursor.advance()
        val sb = StringBuilder()
        while (true) {
            requireOrThrow(!cursor.atEnd) {
                SqlSyntaxException(
                    errorCode,
                    SyntaxErrorDetail(
                        "unterminated ${if (type == TokenType.STRING) "string literal" else "quoted identifier"}",
                        start
                    ),
                )
            }
            val char = cursor.current
            if (char == quote) {
                if (cursor[1] == quote) {
                    sb.append(quote)
                    cursor.advance(2)
                } else {
                    cursor.advance()
                    break
                }
            } else {
                sb.append(char)
                cursor.advance()
            }
        }
        emit(type, start, sb.toString())
    }

    private fun scanOperator(start: Int) {
        val char = cursor.current
        cursor.advance()
        val type: TokenType? = when (char) {
            '(' -> TokenType.LPAREN
            ')' -> TokenType.RPAREN
            ',' -> TokenType.COMMA
            ';' -> TokenType.SEMICOLON
            '.' -> TokenType.DOT
            '*' -> TokenType.STAR
            '+' -> TokenType.PLUS
            '-' -> TokenType.MINUS
            '/' -> TokenType.SLASH
            '%' -> TokenType.PERCENT
            '=' -> TokenType.EQ
            '<' -> {
                val char = cursor.current
                when(char){
                    '=' -> {
                        cursor.advance()
                        TokenType.LE
                    }
                    '>' -> {
                        cursor.advance()
                        TokenType.NE
                    }
                    else -> TokenType.LT
                }
            }

            '>' -> {
                if (cursor.current == '=') {
                    cursor.advance()
                    TokenType.GE
                } else {
                    TokenType.GT
                }
            }

            '!' -> if (cursor.current == '=') {
                cursor.advance(); TokenType.NE
            } else null

            '|' -> if (cursor.current == '|') {
                cursor.advance(); TokenType.CONCAT
            } else null

            else -> null
        }
        emit(
            type ?: throw SqlSyntaxException(
                ErrorCode.UNEXPECTED_CHARACTER,
                SyntaxErrorDetail("unexpected character '$char'", start),
            ),
            start,
        )
    }
}
