package sql.parser

import exception.SqlSyntaxException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import sql.ast.AddColumn
import sql.ast.AllColumns
import sql.ast.Assignment
import sql.ast.Between
import sql.ast.Binary
import sql.ast.BinaryOp
import sql.ast.ColumnDef
import sql.ast.ColumnRef
import sql.ast.CreateIndex
import sql.ast.CreateTable
import sql.ast.Delete
import sql.ast.DropColumn
import sql.ast.DropIndex
import sql.ast.DropTable
import sql.ast.Expr
import sql.ast.ExprItem
import sql.ast.InList
import sql.ast.IndexColumnDef
import sql.ast.Insert
import sql.ast.IsNull
import sql.ast.Like
import sql.ast.Literal
import sql.ast.OrderItem
import sql.ast.PrimaryKeyConstraint
import sql.ast.Select
import sql.ast.Statement
import sql.ast.Unary
import sql.ast.UnaryOp
import sql.ast.Update
import sql.lexer.Lexer
import util.ErrorCode

class ParserTest : BehaviorSpec({
    fun parse(sql: String): Statement = Parser(Lexer(sql).tokenize()).parseStatement()
    fun where(condition: String): Expr = (parse("SELECT * FROM t WHERE $condition") as Select).where!!
    fun col(name: String) = ColumnRef(name)
    fun num(value: Long) = Literal(value)
    fun bin(op: BinaryOp, left: Expr, right: Expr) = Binary(op, left, right)

    fun error(sql: String, code: ErrorCode, offset: Int, message: String? = null) {
        val e = shouldThrowCode<SqlSyntaxException>(code) { parse(sql) }
        e.detail.offset shouldBe offset
        if (message != null) e.message shouldBe message
    }

    given("expression precedence and associativity") {
        `when`("AND and OR are mixed") {
            then("AND binds tighter than OR") {
                where("a = 1 OR b = 2 AND c = 3") shouldBe bin(
                    BinaryOp.OR,
                    bin(BinaryOp.EQ, col("a"), num(1)),
                    bin(
                        BinaryOp.AND,
                        bin(BinaryOp.EQ, col("b"), num(2)),
                        bin(BinaryOp.EQ, col("c"), num(3))
                    ),
                )
            }
        }

        `when`("a chain of the same operator") {
            then("it folds to the left") {
                where("a AND b AND c") shouldBe bin(BinaryOp.AND, bin(BinaryOp.AND, col("a"), col("b")), col("c"))
                where("1 - 2 - 3 = 0") shouldBe bin(
                    BinaryOp.EQ,
                    bin(BinaryOp.SUB, bin(BinaryOp.SUB, num(1), num(2)), num(3)),
                    num(0)
                )
                where("a || b || c = d") shouldBe bin(
                    BinaryOp.EQ,
                    bin(BinaryOp.CONCAT, bin(BinaryOp.CONCAT, col("a"), col("b")), col("c")),
                    col("d"),
                )
            }
        }

        `when`("arithmetic is mixed") {
            then("multiplication binds tighter than addition, and parentheses override both") {
                where("1 + 2 * 3 = x") shouldBe bin(
                    BinaryOp.EQ,
                    bin(BinaryOp.ADD, num(1), bin(BinaryOp.MUL, num(2), num(3))),
                    col("x")
                )
                where("(1 + 2) * 3 = x") shouldBe bin(
                    BinaryOp.EQ,
                    bin(BinaryOp.MUL, bin(BinaryOp.ADD, num(1), num(2)), num(3)),
                    col("x")
                )
                where("a % 2 = 0") shouldBe bin(BinaryOp.EQ, bin(BinaryOp.MOD, col("a"), num(2)), num(0))
            }
        }

        `when`("minus is used as a prefix") {
            then("it binds tighter than multiplication, and can repeat") {
                where("-a * 2 = 0") shouldBe bin(
                    BinaryOp.EQ,
                    bin(BinaryOp.MUL, Unary(UnaryOp.NEG, col("a")), num(2)),
                    num(0)
                )
                where("- -a = 0") shouldBe bin(BinaryOp.EQ, Unary(UnaryOp.NEG, Unary(UnaryOp.NEG, col("a"))), num(0))
            }
        }

        `when`("NOT is used") {
            then("it binds looser than a comparison but tighter than AND") {
                where("NOT a = 1 AND b = 2") shouldBe bin(
                    BinaryOp.AND,
                    Unary(UnaryOp.NOT, bin(BinaryOp.EQ, col("a"), num(1))),
                    bin(BinaryOp.EQ, col("b"), num(2)),
                )
                where("NOT NOT a") shouldBe Unary(UnaryOp.NOT, Unary(UnaryOp.NOT, col("a")))
            }
        }

        `when`("each comparison operator is used") {
            then("it maps to its own BinaryOp") {
                val cases = mapOf(
                    "=" to BinaryOp.EQ, "<>" to BinaryOp.NE, "!=" to BinaryOp.NE, "<" to BinaryOp.LT,
                    "<=" to BinaryOp.LE, ">" to BinaryOp.GT, ">=" to BinaryOp.GE,
                )
                for ((symbol, op) in cases) where("a $symbol 1") shouldBe bin(op, col("a"), num(1))
            }
        }
    }

    given("literals and names") {
        `when`("each literal kind is used") {
            then("it becomes a Literal with the lexer's value") {
                where("a = TRUE") shouldBe bin(BinaryOp.EQ, col("a"), Literal(true))
                where("a = FALSE") shouldBe bin(BinaryOp.EQ, col("a"), Literal(false))
                where("a = NULL") shouldBe bin(BinaryOp.EQ, col("a"), Literal(null))
                where("a = 's'") shouldBe bin(BinaryOp.EQ, col("a"), Literal("s"))
                where("a = 1.5") shouldBe bin(BinaryOp.EQ, col("a"), Literal(BigDecimal("1.5")))
            }
        }

        `when`("names are written in different ways") {
            then("unquoted ones are lowercased and quoted ones keep their case") {
                where("Name = 1") shouldBe bin(BinaryOp.EQ, col("name"), num(1))
                where("\"Name\" = 1") shouldBe bin(BinaryOp.EQ, col("Name"), num(1))
            }
        }
    }

    given("IS NULL, IN, BETWEEN and LIKE") {
        `when`("IS [NOT] NULL") {
            then("it becomes an IsNull node") {
                where("x IS NULL") shouldBe IsNull(col("x"), negated = false)
                where("x IS NOT NULL") shouldBe IsNull(col("x"), negated = true)
            }
        }

        `when`("[NOT] IN (...)") {
            then("it becomes an InList node with the items in order") {
                where("x IN (1, 2, 3)") shouldBe InList(col("x"), listOf(num(1), num(2), num(3)), negated = false)
                where("x NOT IN (1)") shouldBe InList(col("x"), listOf(num(1)), negated = true)
                where("x IN (a + 1, b)") shouldBe InList(
                    col("x"),
                    listOf(bin(BinaryOp.ADD, col("a"), num(1)), col("b")),
                    negated = false
                )
            }
        }

        `when`("BETWEEN is followed by AND") {
            then("that AND belongs to the BETWEEN, and a later AND is a logical one") {
                where("x BETWEEN 1 AND 5 AND y = 2") shouldBe bin(
                    BinaryOp.AND,
                    Between(col("x"), num(1), num(5), negated = false),
                    bin(BinaryOp.EQ, col("y"), num(2)),
                )
                where("x NOT BETWEEN 1 AND 5") shouldBe Between(col("x"), num(1), num(5), negated = true)
                where("x BETWEEN 1 + 1 AND 2 * 3") shouldBe Between(
                    col("x"),
                    bin(BinaryOp.ADD, num(1), num(1)),
                    bin(BinaryOp.MUL, num(2), num(3)),
                    negated = false,
                )
            }
        }

        `when`("[NOT] LIKE") {
            then("it becomes a Like node") {
                where("x LIKE 'a%'") shouldBe Like(col("x"), Literal("a%"), negated = false)
                where("x NOT LIKE 'a%'") shouldBe Like(col("x"), Literal("a%"), negated = true)
            }
        }

        `when`("a parenthesized comparison is compared again") {
            then("it is allowed, since the parentheses end the first comparison") {
                where("(a < b) = TRUE") shouldBe
                        bin(BinaryOp.EQ, bin(BinaryOp.LT, col("a"), col("b")), Literal(true))
            }
        }
    }

    given("SELECT") {
        `when`("selecting everything from a table") {
            then("it has no filter, order or limit") {
                parse("SELECT * FROM t") shouldBe Select(listOf(AllColumns), "t", null, emptyList(), null, null)
            }
        }

        `when`("every clause is used") {
            then("each is read into its own field") {
                parse("SELECT a, b + 1 FROM t WHERE x > 1 ORDER BY a DESC, b LIMIT 5 OFFSET 2") shouldBe Select(
                    columns = listOf(ExprItem(col("a")), ExprItem(bin(BinaryOp.ADD, col("b"), num(1)))),
                    table = "t",
                    where = bin(BinaryOp.GT, col("x"), num(1)),
                    orderBy = listOf(OrderItem("a", true), OrderItem("b", false)),
                    limit = 5,
                    offset = 2,
                )
            }
        }

        `when`("a trailing semicolon is present, or the keywords are lowercase, or the name has capitals") {
            then("they are accepted and the name is lowercased") {
                parse("SELECT * FROM t;") shouldBe parse("SELECT * FROM t")
                parse("select * from T") shouldBe parse("SELECT * FROM t")
                (parse("SELECT * FROM \"T\"") as Select).table shouldBe "T"
            }
        }

        `when`("LIMIT or OFFSET is negative") {
            then("it is parsed so that the engine can reject it with its own error") {
                val select = parse("SELECT * FROM t LIMIT -1 OFFSET -2") as Select
                select.limit shouldBe -1L
                select.offset shouldBe -2L
            }
        }

        `when`("words that are keywords only in some places are used as names") {
            then("they are plain identifiers, including ASC and DESC as columns") {
                parse("SELECT key, table FROM index") shouldBe Select(
                    listOf(ExprItem(col("key")), ExprItem(col("table"))),
                    "index",
                    null,
                    emptyList(),
                    null,
                    null
                )
                (parse("SELECT * FROM t ORDER BY desc") as Select).orderBy shouldBe listOf(OrderItem("desc", false))
                (parse("SELECT * FROM t ORDER BY a ASC") as Select).orderBy shouldBe listOf(OrderItem("a", false))
            }
        }
    }

    given("INSERT, UPDATE and DELETE") {
        `when`("inserting several rows with a column list") {
            then("the rows keep their order") {
                parse("INSERT INTO t (a, b) VALUES (1, 'x'), (2, 'y')") shouldBe Insert(
                    "t",
                    listOf("a", "b"),
                    listOf(listOf(num(1), Literal("x")), listOf(num(2), Literal("y")))
                )
            }
        }

        `when`("inserting without a column list") {
            then("the column list is null") {
                parse("INSERT INTO t VALUES (1)") shouldBe Insert("t", null, listOf(listOf(num(1))))
            }
        }

        `when`("updating with expressions that read the row") {
            then("each assignment keeps its expression") {
                parse("UPDATE t SET a = a + 1, b = 'x' WHERE id = 3") shouldBe Update(
                    "t",
                    listOf(
                        Assignment("a", bin(BinaryOp.ADD, col("a"), num(1))),
                        Assignment("b", Literal("x")),
                    ),
                    bin(BinaryOp.EQ, col("id"), num(3)),
                )
            }
        }

        `when`("deleting with and without a condition") {
            then("the condition is optional") {
                parse("DELETE FROM t") shouldBe Delete("t", null)
                parse("DELETE FROM t WHERE a = 1") shouldBe Delete("t", bin(BinaryOp.EQ, col("a"), num(1)))
            }
        }
    }

    given("CREATE TABLE") {
        `when`("columns carry modifiers and a named table constraint follows") {
            then("each item keeps its form, and the constraint keeps its name") {
                parse("CREATE TABLE t (id INT PRIMARY KEY, name text NOT NULL, CONSTRAINT pk PRIMARY KEY (id))") shouldBe CreateTable(
                    "t",
                    listOf(
                        ColumnDef("id", "int", notNull = false, primaryKey = true),
                        ColumnDef("name", "text", notNull = true, primaryKey = false),
                        PrimaryKeyConstraint(listOf("id"), "pk"),
                    ),
                )
            }
        }

        `when`("a table constraint has several columns and no name") {
            then("the name is null") {
                parse("CREATE TABLE t (a INT, b INT, PRIMARY KEY (a, b))") shouldBe CreateTable(
                    "t",
                    listOf(
                        ColumnDef("a", "int", notNull = false, primaryKey = false),
                        ColumnDef("b", "int", notNull = false, primaryKey = false),
                        PrimaryKeyConstraint(listOf("a", "b")),
                    ),
                )
            }
        }

        `when`("table and column names are words that are keywords only in some places") {
            then("they are accepted as plain names") {
                parse("CREATE TABLE index (key INT, table TEXT)") shouldBe CreateTable(
                    "index",
                    listOf(
                        ColumnDef("key", "int", notNull = false, primaryKey = false),
                        ColumnDef("table", "text", notNull = false, primaryKey = false)
                    ),
                )
            }
        }
    }

    given("CREATE INDEX, DROP and ALTER") {
        `when`("creating indexes") {
            then("UNIQUE and sort directions are read") {
                parse("CREATE INDEX idx ON t (a, b DESC)") shouldBe CreateIndex(
                    "idx",
                    "t",
                    listOf(IndexColumnDef("a", false), IndexColumnDef("b", true)),
                    false
                )
                parse("CREATE UNIQUE INDEX idx ON t (a ASC)") shouldBe CreateIndex(
                    "idx",
                    "t",
                    listOf(IndexColumnDef("a", false)),
                    true
                )
            }
        }

        `when`("dropping") {
            then("tables and indexes are told apart") {
                parse("DROP TABLE t") shouldBe DropTable("t")
                parse("DROP INDEX i") shouldBe DropIndex("i")
            }
        }

        `when`("altering a table") {
            then("COLUMN is optional in both ADD and DROP") {
                val added = AddColumn("t", ColumnDef("c", "int", notNull = false, primaryKey = false))
                parse("ALTER TABLE t ADD COLUMN c INT") shouldBe added
                parse("ALTER TABLE t ADD c INT") shouldBe added
                parse("ALTER TABLE t DROP COLUMN c") shouldBe DropColumn("t", "c")
                parse("ALTER TABLE t DROP c") shouldBe DropColumn("t", "c")
            }
        }
    }

    given("malformed statements") {
        `when`("the first token does not start a statement") {
            then("UNEXPECTED_TOKEN points at it") {
                error(
                    "FOO bar",
                    ErrorCode.UNEXPECTED_TOKEN,
                    0,
                    "[42601/UNEXPECTED_TOKEN] at offset 0: expected a statement but found 'FOO'"
                )
            }
        }

        `when`("an expression is missing") {
            then("UNEXPECTED_TOKEN names the token found instead, or the end of input") {
                error(
                    "SELECT FROM t", ErrorCode.UNEXPECTED_TOKEN, 7,
                    "[42601/UNEXPECTED_TOKEN] at offset 7: expected an expression but found 'FROM'",
                )
                val sql = "SELECT a FROM t WHERE"
                error(
                    sql,
                    ErrorCode.UNEXPECTED_TOKEN,
                    sql.length,
                    "[42601/UNEXPECTED_TOKEN] at offset ${sql.length}: expected an expression but found end of input"
                )
            }
        }

        `when`("a required keyword or name is missing") {
            then("the error says what was expected") {
                error("SELECT a t", ErrorCode.UNEXPECTED_TOKEN, 9)
                error("SELECT a FROM", ErrorCode.UNEXPECTED_TOKEN, 13)
                error("INSERT INTO t VALUES 1", ErrorCode.UNEXPECTED_TOKEN, 21)
                error("UPDATE t SET a 1", ErrorCode.UNEXPECTED_TOKEN, 15)
                error("CREATE TABLE t ()", ErrorCode.UNEXPECTED_TOKEN, 16)
            }
        }

        `when`("tokens are left over after the statement") {
            then("only one trailing semicolon is allowed") {
                error("SELECT * FROM t garbage", ErrorCode.UNEXPECTED_TOKEN, 16)
                error("SELECT * FROM t; SELECT", ErrorCode.UNEXPECTED_TOKEN, 17)
            }
        }

        `when`("comparisons are chained") {
            then("CHAINED_COMPARISON points at the second operator") {
                val sql = "SELECT * FROM t WHERE a < b < c"
                error(sql, ErrorCode.CHAINED_COMPARISON, sql.lastIndexOf("<"))
                val isNull = "SELECT * FROM t WHERE a IS NULL = b"
                error(isNull, ErrorCode.CHAINED_COMPARISON, isNull.lastIndexOf("="))
            }
        }

        `when`("NOT is not followed by IN, BETWEEN or LIKE") {
            then("UNEXPECTED_TOKEN points at the token after NOT") {
                val sql = "SELECT * FROM t WHERE a NOT b"
                error(sql, ErrorCode.UNEXPECTED_TOKEN, sql.lastIndexOf("b"))
            }
        }

        `when`("a parenthesis is never closed") {
            then("UNEXPECTED_TOKEN points at the end of input") {
                val sql = "SELECT * FROM t WHERE (a = 1"
                error(sql, ErrorCode.UNEXPECTED_TOKEN, sql.length)
            }
        }

        `when`("DDL keywords are wrong or quoted") {
            then("the error lists what was expected") {
                error("CREATE \"table\" t (a INT)", ErrorCode.UNEXPECTED_TOKEN, 7)
                error("DROP VIEW v", ErrorCode.UNEXPECTED_TOKEN, 5)
                error("ALTER TABLE t FOO", ErrorCode.UNEXPECTED_TOKEN, 14)
                val named = "CREATE TABLE t (CONSTRAINT c INT)"
                error(named, ErrorCode.UNEXPECTED_TOKEN, named.lastIndexOf("INT"))
            }
        }
    }

    given("deeply nested input") {
        `when`("nesting stays within the limit") {
            then("it parses") {
                where("(".repeat(150) + "1" + ")".repeat(150) + " = 1") shouldBe bin(BinaryOp.EQ, num(1), num(1))
            }
        }

        `when`("parentheses, NOT or minus repeat far beyond the limit") {
            then("NESTING_TOO_DEEP is reported instead of overflowing the stack") {
                for (hostile in listOf(
                    "(".repeat(10_000) + "1" + ")".repeat(10_000),
                    "NOT ".repeat(10_000) + "a",
                    "- ".repeat(10_000) + "a"
                )) {
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.NESTING_TOO_DEEP) {
                        parse("SELECT * FROM t WHERE $hostile")
                    }
                }
            }
        }
    }
})
