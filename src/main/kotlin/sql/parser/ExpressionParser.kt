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
import sql.lexer.TokenType
import util.ErrorCode
import util.SyntaxErrorDetail
import util.requireOrThrow

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

/**
 * Reads one expression. Precedence is the call depth: each rule reads its operands by calling the
 * next tighter-binding rule, so the order below is the precedence table, weakest first:
 * OR, AND, NOT, comparisons / IS / IN / BETWEEN / LIKE, + - ||, * / %, unary minus, primary.
 */
internal class ExpressionParser(
    private val cursor: TokenCursor
) {

    fun parse(): Expr = cursor.nested { parseOr() }

    /** An optional `WHERE <condition>` clause; null when the statement has none. */
    fun parseWhereClause(): Expr? = if (cursor.match(TokenType.WHERE)) parse() else null

    private fun parseOr(): Expr = parseLeftAssociative(::parseAnd, TokenType.OR)

    private fun parseAnd(): Expr = parseLeftAssociative(::parseNot, TokenType.AND)

    private fun parseNot(): Expr =
        if (cursor.match(TokenType.NOT)) Unary(UnaryOp.NOT, cursor.nested { parseNot() })
        else parseComparison()

    private fun parseComparison(): Expr {
        val left = parseAdditive()
        val node = when (cursor.current.type) {
            in COMPARISON_TYPES -> {
                val op = cursor.advance().type
                Binary(BINARY_OPS.getValue(op), left, parseAdditive())
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
                InList(left, cursor.parenthesizedList(::parse), negated)
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

    private fun parseAdditive(): Expr =
        parseLeftAssociative(::parseMultiplicative, TokenType.PLUS, TokenType.MINUS, TokenType.CONCAT)

    private fun parseMultiplicative(): Expr =
        parseLeftAssociative(::parseUnary, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)

    /** `operand (op operand)*`, folded to the left: a - b - c is (a - b) - c. */
    private fun parseLeftAssociative(parseOperand: () -> Expr, vararg operators: TokenType): Expr {
        var left = parseOperand()
        while (cursor.current.type in operators) {
            val op = cursor.advance().type
            left = Binary(BINARY_OPS.getValue(op), left, parseOperand())
        }
        return left
    }

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
                val inner = parse()
                cursor.expect(TokenType.RPAREN, "')'")
                inner
            }

            else -> throw cursor.unexpected("an expression", token)
        }
    }
}
