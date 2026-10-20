package sql.parser

import exception.SqlSyntaxException
import sql.ast.Between
import sql.ast.Binary
import sql.ast.BinaryOp
import sql.ast.ColumnRef
import sql.ast.Expr
import sql.ast.InList
import sql.ast.IsNull
import sql.ast.Like
import sql.ast.Literal
import sql.ast.Unary
import sql.ast.UnaryOp
import sql.lexer.Token
import sql.lexer.TokenType
import util.ErrorCode
import util.SyntaxErrorDetail
import util.requireOrThrow

private val NAME_TYPES = setOf(TokenType.IDENTIFIER, TokenType.QUOTED_IDENTIFIER)

private val COMPARISON_TYPES = setOf(
    TokenType.EQ, TokenType.NE, TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE,
)

private val CHAINING_TYPES = COMPARISON_TYPES + setOf(
    TokenType.IS, TokenType.IN, TokenType.BETWEEN, TokenType.LIKE,
)

private val BINARY_OPS = mapOf(
    TokenType.OR to BinaryOp.OR,
    TokenType.AND to BinaryOp.AND,
    TokenType.EQ to BinaryOp.EQ,
    TokenType.NE to BinaryOp.NE,
    TokenType.LT to BinaryOp.LT,
    TokenType.LE to BinaryOp.LE,
    TokenType.GT to BinaryOp.GT,
    TokenType.GE to BinaryOp.GE,
    TokenType.PLUS to BinaryOp.ADD,
    TokenType.MINUS to BinaryOp.SUB,
    TokenType.STAR to BinaryOp.MUL,
    TokenType.SLASH to BinaryOp.DIV,
    TokenType.PERCENT to BinaryOp.MOD,
    TokenType.CONCAT to BinaryOp.CONCAT,
)

class Parser(tokens: List<Token>) {
    private val cursor = TokenCursor(tokens)

    private fun parseIdent(): String {
        requireOrThrow(cursor.current.type in NAME_TYPES) { cursor.unexpected("identifier") }
        return cursor.advance().value as String
    }

    private fun <T> commaSeparated(parseOne: () -> T): List<T> = buildList {
        add(parseOne())
        while (cursor.match(TokenType.COMMA)) add(parseOne())
    }

    private fun parseInteger(): Long = cursor.expect(TokenType.INTEGER, "integer").value as Long

    private fun parseSignedInteger(): Long {
        val negative = cursor.match(TokenType.MINUS)
        val value = parseInteger()
        return if (negative) -value else value
    }

    private fun parseExpr(): Expr = cursor.nested { parseOr() }

    private fun parseNot(): Expr =
        if (cursor.match(TokenType.NOT)) Unary(UnaryOp.NOT, cursor.nested { parseNot() })
        else parseComparison()

    private fun parseComparison(): Expr {
        val left = parseAdditive()
        val node = when (cursor.current.type) {
            in COMPARISON_TYPES -> {
                val op = cursor.advance().type
                Binary(binaryOpOf(op), left, parseAdditive())
            }

            TokenType.IS -> {
                cursor.advance()
                val negated = cursor.match(TokenType.NOT)
                cursor.expect(TokenType.NULL, "NULL")
                IsNull(left, negated)
            }

            TokenType.NOT, TokenType.IN, TokenType.BETWEEN, TokenType.LIKE -> parsePredicate(left)

            else -> return left
        }
        rejectChainedComparison()
        return node
    }

    /** `[NOT] IN (...)`, `[NOT] BETWEEN ... AND ...`, `[NOT] LIKE ...` after an already-parsed [left] operand. */
    private fun parsePredicate(left: Expr): Expr {
        val negated = cursor.match(TokenType.NOT)
        return when (cursor.current.type) {
            TokenType.IN -> {
                cursor.advance()
                cursor.expect(TokenType.LPAREN, "'('")
                val items = commaSeparated(::parseExpr)
                cursor.expect(TokenType.RPAREN, "')'")
                InList(left, items, negated)
            }

            TokenType.BETWEEN -> {
                cursor.advance()
                val low = parseAdditive()
                cursor.expect(TokenType.AND, "AND")
                Between(left, low, parseAdditive(), negated)
            }

            TokenType.LIKE -> {
                cursor.advance()
                Like(left, parseAdditive(), negated)
            }

            else -> throw cursor.unexpected("IN, BETWEEN or LIKE")
        }
    }

    private fun rejectChainedComparison() {
        requireOrThrow(cursor.current.type !in CHAINING_TYPES) {
            SqlSyntaxException(
                ErrorCode.CHAINED_COMPARISON,
                SyntaxErrorDetail("comparison operators cannot be chained", cursor.current.offset),
            )
        }
    }

    private fun parseOr(): Expr = parseLeftAssociative(::parseAnd, TokenType.OR)

    private fun parseAnd(): Expr = parseLeftAssociative(::parseNot, TokenType.AND)

    private fun parseAdditive(): Expr =
        parseLeftAssociative(::parseMultiplicative, TokenType.PLUS, TokenType.MINUS, TokenType.CONCAT)

    private fun parseMultiplicative(): Expr =
        parseLeftAssociative(::parseUnary, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)

    /** `operand (op operand)*`, folded to the left: a - b - c is (a - b) - c. */
    private fun parseLeftAssociative(parseOperand: () -> Expr, vararg operators: TokenType): Expr {
        var left = parseOperand()
        while (cursor.current.type in operators) {
            val op = cursor.advance().type
            left = Binary(binaryOpOf(op), left, parseOperand())
        }
        return left
    }

    private fun binaryOpOf(type: TokenType): BinaryOp = BINARY_OPS.getValue(type)

    private fun parseUnary(): Expr =
        if (cursor.match(TokenType.MINUS)) Unary(UnaryOp.NEG, cursor.nested { parseUnary() })
        else parsePrimary()

    private fun parsePrimary(): Expr {
        val token = cursor.advance()
        return when (token.type) {
            TokenType.INTEGER, TokenType.DECIMAL, TokenType.STRING -> Literal(token.value)
            TokenType.TRUE -> Literal(true)
            TokenType.FALSE -> Literal(false)
            TokenType.NULL -> Literal(null)
            TokenType.IDENTIFIER, TokenType.QUOTED_IDENTIFIER -> ColumnRef(token.value as String)
            TokenType.LPAREN -> {
                val inner = parseExpr()
                cursor.expect(TokenType.RPAREN, "')'")
                inner
            }

            else -> throw cursor.unexpected("an expression", token)
        }
    }
}
