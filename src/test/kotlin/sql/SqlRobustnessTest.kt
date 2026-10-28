package sql

import exception.SqlSyntaxException
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import kotlin.random.Random
import sql.ast.Between
import sql.ast.Binary
import sql.ast.BinaryOp
import sql.ast.ColumnRef
import sql.ast.Expr
import sql.ast.InList
import sql.ast.IsNull
import sql.ast.Like
import sql.ast.Literal
import sql.ast.Select
import sql.ast.Statement
import sql.ast.Unary
import sql.ast.UnaryOp
import sql.lexer.Lexer
import sql.lexer.Token
import sql.lexer.TokenType
import sql.parser.Parser
import util.ErrorCode

private val LEXER_ERRORS = setOf(
    ErrorCode.UNTERMINATED_STRING,
    ErrorCode.UNTERMINATED_QUOTED_IDENTIFIER,
    ErrorCode.UNTERMINATED_COMMENT,
    ErrorCode.EMPTY_QUOTED_IDENTIFIER,
    ErrorCode.UNEXPECTED_CHARACTER,
    ErrorCode.INVALID_NUMBER,
    ErrorCode.NUMBER_OUT_OF_RANGE,
)

private val PARSER_ERRORS = LEXER_ERRORS + setOf(
    ErrorCode.UNEXPECTED_TOKEN,
    ErrorCode.CHAINED_COMPARISON,
    ErrorCode.NESTING_TOO_DEEP,
)

private val VALID_STATEMENTS = listOf(
    "SELECT * FROM t",
    "SELECT a, b + 1 FROM t WHERE x > 1 AND y IS NOT NULL ORDER BY a DESC, b LIMIT 5 OFFSET 2",
    "SELECT * FROM t WHERE a IN (1, 2, 3) OR b BETWEEN 1 AND 5 OR c NOT LIKE 'x%'",
    "INSERT INTO t (a, b) VALUES (1, 'x'), (2, NULL)",
    "UPDATE t SET a = a + 1, b = 'y' WHERE id = 3",
    "DELETE FROM t WHERE a = 1 OR NOT (b = 2)",
    "CREATE TABLE t (id INT NOT NULL PRIMARY KEY, name text, CONSTRAINT pk PRIMARY KEY (id))",
    "CREATE UNIQUE INDEX idx ON t (a, b DESC)",
    "DROP TABLE t",
    "DROP INDEX idx",
    "ALTER TABLE t ADD COLUMN c INT NOT NULL",
    "ALTER TABLE t DROP COLUMN c",
)

private val SOUP = listOf(
    "SELECT", "select", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
    "TABLE", "table", "INDEX", "index", "UNIQUE", "unique", "ON", "DROP", "ALTER", "ADD", "add", "COLUMN",
    "PRIMARY", "KEY", "key", "CONSTRAINT", "NOT", "NULL", "AND", "OR", "IS", "IN", "BETWEEN", "LIKE", "TRUE",
    "FALSE", "ORDER", "BY", "ASC", "asc", "DESC", "desc", "LIMIT", "OFFSET", "AS", "a", "b", "t", "\"q\"",
    "1", "-2", "1.5", "'x'", "''", "(", ")", ",", ";", "*", "+", "-", "/", "%", "||", "=", "<>", "<=", ">=",
    "<", ">", "!=", ".", "--c\n", "/*c*/",
)

private const val CHARS = $$"abcXYZ_09 .'\"-/*<>=!|(),;\n\t$@é한😀\u0000"

/** Prints [expr] as SQL with only the parentheses its precedence requires - the parser's inverse. */
private fun render(expr: Expr, minPrecedence: Int): String {
    val (text, precedence) = renderWithPrecedence(expr)
    return if (precedence < minPrecedence) "($text)" else text
}

private fun renderWithPrecedence(expr: Expr): Pair<String, Int> = when (expr) {
    is Literal -> literalText(expr.value) to 8
    is ColumnRef -> expr.name to 8
    is Unary -> when (expr.op) {
        UnaryOp.NOT -> "NOT ${render(expr.operand, 3)}" to 3
        UnaryOp.NEG -> "- ${render(expr.operand, 7)}" to 7
    }

    is Binary -> {
        val (leftMin, rightMin, precedence) = when (expr.op) {
            BinaryOp.OR -> Triple(1, 2, 1)
            BinaryOp.AND -> Triple(2, 3, 2)
            BinaryOp.EQ, BinaryOp.NE, BinaryOp.LT, BinaryOp.LE, BinaryOp.GT, BinaryOp.GE -> Triple(5, 5, 4)
            BinaryOp.ADD, BinaryOp.SUB, BinaryOp.CONCAT -> Triple(5, 6, 5)
            BinaryOp.MUL, BinaryOp.DIV, BinaryOp.MOD -> Triple(6, 7, 6)
        }
        "${render(expr.left, leftMin)} ${symbolOf(expr.op)} ${render(expr.right, rightMin)}" to precedence
    }

    is IsNull -> "${render(expr.operand, 5)} IS ${if (expr.negated) "NOT " else ""}NULL" to 4
    is InList ->
        "${render(expr.operand, 5)} ${if (expr.negated) "NOT " else ""}IN (${
            expr.items.joinToString(", ") {
                render(
                    it,
                    1
                )
            }
        })" to 4

    is Between ->
        "${render(expr.operand, 5)} ${if (expr.negated) "NOT " else ""}BETWEEN ${
            render(
                expr.low,
                5
            )
        } AND ${render(expr.high, 5)}" to 4

    is Like ->
        "${render(expr.operand, 5)} ${if (expr.negated) "NOT " else ""}LIKE ${render(expr.pattern, 5)}" to 4
}

private fun literalText(value: Any?): String = when (value) {
    null -> "NULL"
    is Boolean -> if (value) "TRUE" else "FALSE"
    is String -> "'" + value.replace("'", "''") + "'"
    else -> value.toString()
}

private fun symbolOf(op: BinaryOp) = when (op) {
    BinaryOp.OR -> "OR"
    BinaryOp.AND -> "AND"
    BinaryOp.EQ -> "="
    BinaryOp.NE -> "<>"
    BinaryOp.LT -> "<"
    BinaryOp.LE -> "<="
    BinaryOp.GT -> ">"
    BinaryOp.GE -> ">="
    BinaryOp.ADD -> "+"
    BinaryOp.SUB -> "-"
    BinaryOp.MUL -> "*"
    BinaryOp.DIV -> "/"
    BinaryOp.MOD -> "%"
    BinaryOp.CONCAT -> "||"
}

private fun randomLeaf(random: Random): Expr = when (random.nextInt(4)) {
    0 -> ColumnRef(listOf("a", "b", "c", "key", "table", "desc")[random.nextInt(6)])
    1 -> Literal(listOf(0L, 1L, 7L, 123456789L, Long.MAX_VALUE)[random.nextInt(5)])
    2 -> Literal(listOf("a", "it's", "", "x y", "%a_")[random.nextInt(5)])
    else -> when (random.nextInt(4)) {
        0 -> Literal(null)
        1 -> Literal(true)
        2 -> Literal(false)
        else -> Literal(BigDecimal(listOf("1.5", "0.25", "10.0", "3.14")[random.nextInt(4)]))
    }
}

private fun randomExpr(random: Random, depth: Int): Expr {
    if (depth == 0) return randomLeaf(random)
    fun sub() = randomExpr(random, depth - 1)
    return when (random.nextInt(11)) {
        0 -> randomLeaf(random)
        1 -> Binary(listOf(BinaryOp.OR, BinaryOp.AND)[random.nextInt(2)], sub(), sub())
        2 -> Binary(
            listOf(BinaryOp.EQ, BinaryOp.NE, BinaryOp.LT, BinaryOp.LE, BinaryOp.GT, BinaryOp.GE)[random.nextInt(6)],
            sub(), sub(),
        )

        3 -> Binary(listOf(BinaryOp.ADD, BinaryOp.SUB, BinaryOp.CONCAT)[random.nextInt(3)], sub(), sub())
        4 -> Binary(listOf(BinaryOp.MUL, BinaryOp.DIV, BinaryOp.MOD)[random.nextInt(3)], sub(), sub())
        5 -> Unary(UnaryOp.NOT, sub())
        6 -> Unary(UnaryOp.NEG, sub())
        7 -> IsNull(sub(), random.nextBoolean())
        8 -> InList(sub(), List(1 + random.nextInt(3)) { sub() }, random.nextBoolean())
        9 -> Between(sub(), sub(), sub(), random.nextBoolean())
        else -> Like(sub(), sub(), random.nextBoolean())
    }
}

class SqlRobustnessTest :
    BehaviorSpec({
        timeout = 120_000L

        fun parse(sql: String): Statement = Parser(Lexer(sql).tokenize()).parseStatement()

        given("the lexer on random character soup") {
            `when`("tokenizing thousands of short random strings") {
                then("it either returns well-formed tokens or throws a lexer error, never anything else") {
                    val random = Random(20261027)
                    var succeeded = 0
                    repeat(40_000) {
                        val input = String(CharArray(random.nextInt(0, 26)) { CHARS[random.nextInt(CHARS.length)] })
                        withClue("input: ${input.map { c -> c.code }}") {
                            val result = try {
                                Lexer(input).tokenize()
                            } catch (e: SqlSyntaxException) {
                                e.code shouldBeIn LEXER_ERRORS
                                (e.detail.offset in 0..input.length) shouldBe true
                                return@withClue
                            }
                            succeeded++
                            result.last().type shouldBe TokenType.EOF
                            result.last().offset shouldBe input.length
                            var previousEnd = 0
                            for (token in result.dropLast(1)) {
                                token.text.isNotEmpty() shouldBe true
                                (token.offset >= previousEnd) shouldBe true
                                input.substring(token.offset, token.offset + token.text.length) shouldBe token.text
                                previousEnd = token.offset + token.text.length
                            }
                        }
                    }
                    succeeded shouldBeGreaterThan 1_000
                }
            }
        }

        given("the parser on shuffled SQL vocabulary") {
            `when`("parsing thousands of random token sequences") {
                then("it either returns a statement or throws a syntax error, never anything else") {
                    val random = Random(20261028)
                    repeat(40_000) {
                        val input = List(random.nextInt(1, 14)) { SOUP[random.nextInt(SOUP.size)] }.joinToString(" ")
                        withClue("input: $input") {
                            try {
                                parse(input)
                            } catch (e: SqlSyntaxException) {
                                e.code shouldBeIn PARSER_ERRORS
                                (e.detail.offset in 0..input.length) shouldBe true
                            }
                        }
                    }
                }
            }
        }

        given("the parser on slightly broken valid statements") {
            `when`("deleting, duplicating, swapping or replacing one token of a valid statement") {
                then("it either parses or throws a syntax error, and parsing twice gives the same answer") {
                    val random = Random(20261029)
                    var parsed = 0
                    var rejected = 0
                    repeat(40_000) {
                        val source = VALID_STATEMENTS[random.nextInt(VALID_STATEMENTS.size)]
                        val pieces: MutableList<String> =
                            Lexer(source).tokenize().filter { it: Token ->
                                it.type != TokenType.EOF
                            }.map {
                                it.text
                            }.toMutableList()
                        when (random.nextInt(4)) {
                            0 -> pieces.removeAt(random.nextInt(pieces.size))
                            1 -> pieces.add(random.nextInt(pieces.size), pieces[random.nextInt(pieces.size)])
                            2 -> if (pieces.size > 1) {
                                val i = random.nextInt(pieces.size - 1)
                                pieces[i] = pieces[i + 1].also { pieces[i + 1] = pieces[i] }
                            }

                            else -> pieces[random.nextInt(pieces.size)] = SOUP[random.nextInt(SOUP.size)]
                        }
                        val input = pieces.joinToString(" ")
                        withClue("input: $input") {
                            val first = try {
                                parse(input)
                            } catch (e: SqlSyntaxException) {
                                e.code shouldBeIn PARSER_ERRORS
                                (e.detail.offset in 0..input.length) shouldBe true
                                rejected++
                                return@withClue
                            }
                            parsed++
                            parse(input) shouldBe first
                        }
                    }
                    parsed shouldBeGreaterThan 500
                    rejected shouldBeGreaterThan 500
                }
            }
        }

        given("printing an expression and parsing it back") {
            `when`("random expression trees are printed with only the parentheses precedence requires") {
                then("parsing the text gives back exactly the same tree") {
                    val random = Random(20261030)
                    repeat(8_000) {
                        val expr = randomExpr(random, depth = 5)
                        val text = render(expr, 1)
                        withClue("expression: $text") {
                            val select = parse("SELECT * FROM t WHERE $text") as Select
                            select.where shouldBe expr
                        }
                    }
                }
            }
        }
    })
