package sql.parser

import exception.SqlSyntaxException
import helper.shouldThrowCode
import io.kotest.assertions.throwables.shouldThrow
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

class ParserEdgeCasesTest :
    BehaviorSpec({
        fun parse(sql: String): Statement = Parser(Lexer(sql).tokenize()).parseStatement()
        fun where(condition: String): Expr = (parse("SELECT * FROM t WHERE $condition") as Select).where!!
        fun col(name: String) = ColumnRef(name)
        fun num(value: Long) = Literal(value)
        fun bin(op: BinaryOp, left: Expr, right: Expr) = Binary(op, left, right)
        fun notNull(name: String, type: String = "int") = ColumnDef(name, type, notNull = false, primaryKey = false)

        fun message(offset: Int, expected: String, found: String) =
            "[42601/UNEXPECTED_TOKEN] at offset $offset: expected $expected but found $found"

        fun errorAtOffset(sql: String, offset: Int, found: String, expected: String) {
            val e = shouldThrowCode<SqlSyntaxException>(ErrorCode.UNEXPECTED_TOKEN) { parse(sql) }
            e.message shouldBe message(offset, expected, found)
        }

        /** The unexpected token is the last occurrence of [foundText] in [sql]. */
        fun errorAt(sql: String, foundText: String, expected: String) =
            errorAtOffset(sql, sql.lastIndexOf(foundText), "'$foundText'", expected)

        fun errorAtEnd(sql: String, expected: String) = errorAtOffset(sql, sql.length, "end of input", expected)

        fun errorWithCode(sql: String, code: ErrorCode, offset: Int) {
            val e = shouldThrowCode<SqlSyntaxException>(code) { parse(sql) }
            e.detail.offset shouldBe offset
        }

        given("expression combinations") {
            `when`("NOT meets IS NULL, and IS NOT NULL meets AND") {
                then("NOT wraps the whole predicate; IS NOT NULL stays one node") {
                    where("NOT a IS NULL") shouldBe Unary(UnaryOp.NOT, IsNull(col("a"), negated = false))
                    where("a IS NOT NULL AND b") shouldBe bin(BinaryOp.AND, IsNull(col("a"), negated = true), col("b"))
                }
            }

            `when`("concatenation meets LIKE, and LIKE's pattern is an expression") {
                then("concatenation binds tighter on both sides") {
                    where("a || b LIKE 'x%'") shouldBe
                            Like(bin(BinaryOp.CONCAT, col("a"), col("b")), Literal("x%"), negated = false)
                    where("a LIKE b || '%'") shouldBe
                            Like(col("a"), bin(BinaryOp.CONCAT, col("b"), Literal("%")), negated = false)
                }
            }

            `when`("a negative number or a parenthesized group appears") {
                then("minus is a unary operator, and parentheses regroup NOT, OR and AND") {
                    where("a = -1") shouldBe bin(BinaryOp.EQ, col("a"), Unary(UnaryOp.NEG, num(1)))
                    where("NOT (a OR b)") shouldBe Unary(UnaryOp.NOT, bin(BinaryOp.OR, col("a"), col("b")))
                    where("(a OR b) AND c") shouldBe bin(BinaryOp.AND, bin(BinaryOp.OR, col("a"), col("b")), col("c"))
                }
            }

            `when`("both sides of a comparison are expressions") {
                then("arithmetic binds tighter than the comparison") {
                    where("a + 1 = b * 2") shouldBe
                            bin(
                                BinaryOp.EQ,
                                bin(BinaryOp.ADD, col("a"), num(1)),
                                bin(BinaryOp.MUL, col("b"), num(2)),
                            )
                    where("a < b AND b < c") shouldBe
                            bin(BinaryOp.AND, bin(BinaryOp.LT, col("a"), col("b")), bin(BinaryOp.LT, col("b"), col("c")))
                    where("a = 1 AND NOT b = 2") shouldBe
                            bin(
                                BinaryOp.AND,
                                bin(BinaryOp.EQ, col("a"), num(1)),
                                Unary(UnaryOp.NOT, bin(BinaryOp.EQ, col("b"), num(2))),
                            )
                }
            }

            `when`("several BETWEEN predicates are joined by AND") {
                then("each keeps its own bounds, which can be columns") {
                    where("x BETWEEN 1 AND 2 AND y BETWEEN 3 AND 4") shouldBe
                            bin(
                                BinaryOp.AND,
                                Between(col("x"), num(1), num(2), negated = false),
                                Between(col("y"), num(3), num(4), negated = false),
                            )
                    where("x BETWEEN a AND b") shouldBe Between(col("x"), col("a"), col("b"), negated = false)
                }
            }

            `when`("IN lists hold parenthesized expressions, or NOT precedes a negated IN") {
                then("items are full expressions, and NOT wraps the negated predicate") {
                    where("x IN ((1 + 2), 3)") shouldBe
                            InList(col("x"), listOf(bin(BinaryOp.ADD, num(1), num(2)), num(3)), negated = false)
                    where("NOT x NOT IN (1)") shouldBe
                            Unary(UnaryOp.NOT, InList(col("x"), listOf(num(1)), negated = true))
                }
            }

            `when`("names are words that are keywords only in some places, or are quoted") {
                then("they are ordinary columns") {
                    where("key = 1 AND desc > asc") shouldBe
                            bin(
                                BinaryOp.AND,
                                bin(BinaryOp.EQ, col("key"), num(1)),
                                bin(BinaryOp.GT, col("desc"), col("asc")),
                            )
                    where("table = index") shouldBe bin(BinaryOp.EQ, col("table"), col("index"))
                    where("\"select\" = 1") shouldBe bin(BinaryOp.EQ, col("select"), num(1))
                }
            }

            `when`("literals carry escapes, exponents, or sit in odd places") {
                then("the lexer's value is kept") {
                    where("a = 'it''s'") shouldBe bin(BinaryOp.EQ, col("a"), Literal("it's"))
                    where("a = 1e3") shouldBe bin(BinaryOp.EQ, col("a"), Literal(BigDecimal("1e3")))
                    where("1 = 1") shouldBe bin(BinaryOp.EQ, num(1), num(1))
                    where("NULL IS NULL") shouldBe IsNull(Literal(null), negated = false)
                }
            }

            `when`("comments, newlines and missing spaces surround the operators") {
                then("they do not change the tree") {
                    where("a = 1 -- first\n AND /* second */ b = 2") shouldBe
                            bin(BinaryOp.AND, bin(BinaryOp.EQ, col("a"), num(1)), bin(BinaryOp.EQ, col("b"), num(2)))
                    where("a=1 AND b<>2 OR c>=3") shouldBe
                            bin(
                                BinaryOp.OR,
                                bin(BinaryOp.AND, bin(BinaryOp.EQ, col("a"), num(1)), bin(BinaryOp.NE, col("b"), num(2))),
                                bin(BinaryOp.GE, col("c"), num(3)),
                            )
                }
            }
        }

        given("chained comparisons") {
            `when`("a comparison, IS NULL, IN, BETWEEN or LIKE is followed by another of them") {
                then("CHAINED_COMPARISON points at the second operator") {
                    val cases = listOf(
                        "a = b = c" to "=",
                        "a IS NULL IS NULL" to "IS",
                        "a IN (1) = TRUE" to "=",
                        "a BETWEEN 1 AND 2 BETWEEN 3 AND 4" to "BETWEEN",
                        "a LIKE 'x' LIKE 'y'" to "LIKE",
                        "a < b IS NULL" to "IS",
                    )
                    for ((condition, secondOperator) in cases) {
                        val sql = "SELECT * FROM t WHERE $condition"
                        errorWithCode(sql, ErrorCode.CHAINED_COMPARISON, sql.lastIndexOf(secondOperator))
                    }
                }
            }

            `when`("a negated predicate follows a comparison") {
                then("it is rejected as well") {
                    shouldThrow<SqlSyntaxException> { parse("SELECT * FROM t WHERE a = 1 NOT IN (2)") }
                }
            }
        }

        given("SELECT variations") {
            `when`("only LIMIT or only OFFSET is given") {
                then("the other stays null") {
                    parse("SELECT * FROM t LIMIT 3") shouldBe Select(listOf(AllColumns), "t", null, emptyList(), 3, null)
                    parse("SELECT * FROM t OFFSET 5") shouldBe Select(listOf(AllColumns), "t", null, emptyList(), null, 5)
                }
            }

            `when`("ORDER BY lists several columns with and without directions") {
                then("ASC and no direction are both ascending") {
                    (parse("SELECT * FROM t ORDER BY a ASC, b DESC, c") as Select).orderBy shouldBe
                            listOf(OrderItem("a", false), OrderItem("b", true), OrderItem("c", false))
                }
            }

            `when`("the select list holds literals") {
                then("each is an expression item") {
                    (parse("SELECT 1, 'a', NULL FROM t") as Select).columns shouldBe
                            listOf(ExprItem(num(1)), ExprItem(Literal("a")), ExprItem(Literal(null)))
                }
            }

            `when`("reserved words are quoted to be used as names") {
                then("quoting keeps both the word and its case") {
                    parse("SELECT \"from\", \"Select\" FROM \"T\"") shouldBe
                            Select(listOf(ExprItem(col("from")), ExprItem(col("Select"))), "T", null, emptyList(), null, null)
                }
            }

            `when`("comments, odd casing and trailing text after the semicolon's line surround the statement") {
                then("the result is the same as the plain statement") {
                    val plain = parse("SELECT * FROM t")
                    parse("SELECT /* c */ * -- all\nFROM t -- done\n;") shouldBe plain
                    parse("SELECT * FROM t;   -- trailing") shouldBe plain
                    parse("SeLeCt * FrOm T") shouldBe plain
                }
            }
        }

        given("INSERT variations") {
            `when`("values are expressions") {
                then("each is parsed as a full expression") {
                    parse("INSERT INTO t VALUES (1 + 1, -1, NULL, 'x', TRUE)") shouldBe
                            Insert(
                                "t",
                                null,
                                listOf(
                                    listOf(
                                        bin(BinaryOp.ADD, num(1), num(1)),
                                        Unary(UnaryOp.NEG, num(1)),
                                        Literal(null),
                                        Literal("x"),
                                        Literal(true),
                                    ),
                                ),
                            )
                    parse("INSERT INTO t VALUES ((1 + 2) * 3)") shouldBe
                            Insert("t", null, listOf(listOf(bin(BinaryOp.MUL, bin(BinaryOp.ADD, num(1), num(2)), num(3)))))
                }
            }

            `when`("there are three rows, quoted names, or words that are keywords only in some places as columns") {
                then("rows keep their order and names are resolved as usual") {
                    parse("INSERT INTO t (a) VALUES (1), (2), (3)") shouldBe
                            Insert("t", listOf("a"), listOf(listOf(num(1)), listOf(num(2)), listOf(num(3))))
                    parse("INSERT INTO \"T\" (\"A\") VALUES (1)") shouldBe Insert("T", listOf("A"), listOf(listOf(num(1))))
                    parse("INSERT INTO t (key, table) VALUES (1, 2);") shouldBe
                            Insert("t", listOf("key", "table"), listOf(listOf(num(1), num(2))))
                }
            }
        }

        given("UPDATE and DELETE variations") {
            `when`("an UPDATE has no WHERE, or assigns expressions and columns") {
                then("each assignment keeps its right-hand side") {
                    parse("UPDATE t SET a = 1") shouldBe Update("t", listOf(Assignment("a", num(1))), null)
                    parse("UPDATE t SET a = (b + 1) * 2") shouldBe
                            Update(
                                "t",
                                listOf(Assignment("a", bin(BinaryOp.MUL, bin(BinaryOp.ADD, col("b"), num(1)), num(2)))),
                                null,
                            )
                    parse("UPDATE t SET a = b, b = a") shouldBe
                            Update("t", listOf(Assignment("a", col("b")), Assignment("b", col("a"))), null)
                }
            }

            `when`("an UPDATE uses words that are keywords only in some places, quoted names and a grouped WHERE") {
                then("they resolve as ordinary names and the grouping is kept") {
                    parse("UPDATE t SET key = 1 WHERE desc = 2 AND (a = 1 OR b = 2)") shouldBe
                            Update(
                                "t",
                                listOf(Assignment("key", num(1))),
                                bin(
                                    BinaryOp.AND,
                                    bin(BinaryOp.EQ, col("desc"), num(2)),
                                    bin(BinaryOp.OR, bin(BinaryOp.EQ, col("a"), num(1)), bin(BinaryOp.EQ, col("b"), num(2))),
                                ),
                            )
                    parse("UPDATE \"T\" SET \"A\" = 1") shouldBe Update("T", listOf(Assignment("A", num(1))), null)
                }
            }

            `when`("a DELETE has a mixed condition, or a trailing semicolon") {
                then("it parses the full condition") {
                    parse("DELETE FROM t WHERE a = 1 OR b = 2 AND NOT c") shouldBe
                            Delete(
                                "t",
                                bin(
                                    BinaryOp.OR,
                                    bin(BinaryOp.EQ, col("a"), num(1)),
                                    bin(BinaryOp.AND, bin(BinaryOp.EQ, col("b"), num(2)), Unary(UnaryOp.NOT, col("c"))),
                                ),
                            )
                    parse("DELETE FROM t;") shouldBe Delete("t", null)
                }
            }
        }

        given("CREATE TABLE variations") {
            `when`("NOT NULL and PRIMARY KEY are written in either order, or NOT NULL is repeated") {
                then("both flags are set regardless of order") {
                    val expected = CreateTable("t", listOf(ColumnDef("id", "int", notNull = true, primaryKey = true)))
                    parse("CREATE TABLE t (id INT NOT NULL PRIMARY KEY)") shouldBe expected
                    parse("CREATE TABLE t (id INT PRIMARY KEY NOT NULL)") shouldBe expected
                    parse("CREATE TABLE t (id INT NOT NULL NOT NULL PRIMARY KEY)") shouldBe expected
                }
            }

            `when`("the table constraint comes first, or is named with several columns") {
                then("items keep their written order") {
                    parse("CREATE TABLE t (PRIMARY KEY (id), id INT)") shouldBe
                            CreateTable("t", listOf(PrimaryKeyConstraint(listOf("id")), notNull("id")))
                    parse("CREATE TABLE t (CONSTRAINT pk PRIMARY KEY (a, b), c INT)") shouldBe
                            CreateTable("t", listOf(PrimaryKeyConstraint(listOf("a", "b"), "pk"), notNull("c")))
                }
            }

            `when`("names are quoted, and types differ") {
                then("quoted names keep their case and type names are lowercased") {
                    parse("CREATE TABLE \"Order\" (\"Id\" INT, \"Name\" TEXT)") shouldBe
                            CreateTable("Order", listOf(notNull("Id"), notNull("Name", "text")))
                    parse("CREATE TABLE t (a BIGINT, b VARCHAR, c DOUBLE, d BOOLEAN)") shouldBe
                            CreateTable(
                                "t",
                                listOf(notNull("a", "bigint"), notNull("b", "varchar"), notNull("c", "double"), notNull("d", "boolean")),
                            )
                    parse("create table T (A int);") shouldBe CreateTable("t", listOf(notNull("a")))
                }
            }
        }

        given("CREATE INDEX, DROP and ALTER variations") {
            `when`("an index has several columns with different directions, or odd names") {
                then("each column keeps its own direction") {
                    parse("CREATE INDEX idx ON t (a ASC, b DESC, c)") shouldBe
                            CreateIndex(
                                "idx",
                                "t",
                                listOf(IndexColumnDef("a", false), IndexColumnDef("b", true), IndexColumnDef("c", false)),
                                unique = false,
                            )
                    parse("CREATE UNIQUE INDEX u ON t (a DESC)") shouldBe
                            CreateIndex("u", "t", listOf(IndexColumnDef("a", true)), unique = true)
                    parse("CREATE INDEX key ON table (desc)") shouldBe
                            CreateIndex("key", "table", listOf(IndexColumnDef("desc", false)), unique = false)
                    parse("CREATE INDEX \"Idx\" ON \"T\" (\"A\" DESC)") shouldBe
                            CreateIndex("Idx", "T", listOf(IndexColumnDef("A", true)), unique = false)
                }
            }

            `when`("dropping with quoted or mixed-case names") {
                then("the usual folding rules apply") {
                    parse("DROP TABLE \"T\"") shouldBe DropTable("T")
                    parse("drop index I") shouldBe DropIndex("i")
                }
            }

            `when`("altering with modifiers, odd casing, or a column named like a keyword that is one only in some places") {
                then("ADD takes a full column definition and DROP takes a name") {
                    parse("ALTER TABLE t ADD COLUMN c INT NOT NULL") shouldBe
                            AddColumn("t", ColumnDef("c", "int", notNull = true, primaryKey = false))
                    parse("ALTER TABLE t ADD c INT PRIMARY KEY") shouldBe
                            AddColumn("t", ColumnDef("c", "int", notNull = false, primaryKey = true))
                    parse("ALTER TABLE t ADD add INT") shouldBe AddColumn("t", notNull("add"))
                    parse("ALTER TABLE t DROP COLUMN \"C\"") shouldBe DropColumn("t", "C")
                    parse("Alter Table T Add Column C Int") shouldBe AddColumn("t", notNull("c"))
                    parse("ALTER TABLE t DROP COLUMN c;") shouldBe DropColumn("t", "c")
                }
            }
        }

        given("errors in SELECT") {
            `when`("a clause is cut short or given the wrong kind of value") {
                then("the message names what was expected and what was found") {
                    errorAtEnd("SELECT * FROM t ORDER", "BY")
                    errorAtEnd("SELECT * FROM t ORDER BY", "identifier")
                    errorAtEnd("SELECT * FROM t LIMIT", "integer")
                    errorAtEnd("SELECT * FROM t OFFSET", "integer")
                    errorAt("SELECT * FROM t LIMIT 1.5", "1.5", "integer")
                    errorAt("SELECT * FROM t LIMIT 'x'", "'x'", "integer")
                }
            }

            `when`("a predicate is incomplete") {
                then("the error points at the offending token or the end of input") {
                    errorAt("SELECT * FROM t WHERE a IN ()", ")", "an expression")
                    errorAt("SELECT * FROM t WHERE a IN (1,)", ")", "an expression")
                    errorAt("SELECT * FROM t WHERE a IN 1", "1", "'('")
                    errorAt("SELECT * FROM t WHERE a BETWEEN 1 5", "5", "AND")
                    errorAt("SELECT * FROM t WHERE a IS 5", "5", "NULL")
                    errorAtEnd("SELECT * FROM t WHERE a LIKE", "an expression")
                    errorAtEnd("SELECT * FROM t WHERE a =", "an expression")
                    errorAtEnd("SELECT * FROM t WHERE a +", "an expression")
                }
            }

            `when`("reserved words stand where names belong") {
                then("they are rejected unless quoted") {
                    errorAt("SELECT select FROM t", "select", "an expression")
                    errorAt("SELECT * FROM select", "select", "identifier")
                    parse("SELECT \"select\" FROM \"from\"") shouldBe
                            Select(listOf(ExprItem(col("select"))), "from", null, emptyList(), null, null)
                }
            }

            `when`("a statement runs across lines") {
                then("the offset counts newlines like any other character") {
                    errorAt("SELECT *\nFROM t\nWHERE a NOT b", "b", "IN, BETWEEN or LIKE")
                }
            }
        }

        given("errors in INSERT, UPDATE and DELETE") {
            `when`("an INSERT is incomplete") {
                then("each missing piece is named") {
                    errorAt("INSERT t VALUES (1)", "t", "INTO")
                    errorAtEnd("INSERT INTO t", "VALUES")
                    errorAt("INSERT INTO t VALUES ()", ")", "an expression")
                    val emptyColumns = "INSERT INTO t () VALUES (1)"
                    errorAtOffset(emptyColumns, emptyColumns.indexOf("()") + 1, "')'", "identifier")
                    errorAtEnd("INSERT INTO t VALUES (1", "')'")
                    errorAtEnd("INSERT INTO t VALUES (1),", "'('")
                    errorAt("INSERT INTO t (values) VALUES (1)", "values", "identifier")
                }
            }

            `when`("an UPDATE or DELETE is incomplete") {
                then("each missing piece is named") {
                    errorAt("UPDATE t a = 1", "a", "SET")
                    errorAtEnd("UPDATE t SET", "identifier")
                    errorAtEnd("UPDATE t SET a =", "an expression")
                    errorAtEnd("UPDATE t SET a = 1,", "identifier")
                    errorAt("DELETE t", "t", "FROM")
                    errorAtEnd("DELETE FROM", "identifier")
                }
            }
        }

        given("errors in DDL") {
            `when`("a CREATE INDEX is incomplete or malformed") {
                then("each case is named") {
                    errorAt("CREATE UNIQUE t", "t", "INDEX")
                    errorAt("CREATE INDEX i t (a)", "t", "ON")
                    errorAt("CREATE INDEX i ON t ()", ")", "identifier")
                    errorAt("CREATE INDEX i ON t (a,)", ")", "identifier")
                }
            }

            `when`("a CREATE TABLE is incomplete or malformed") {
                then("each case is named") {
                    errorAt("CREATE TABLE t (a)", ")", "identifier")
                    errorAt("CREATE TABLE t (a INT NOT)", ")", "NULL")
                    errorAt("CREATE TABLE t (a INT PRIMARY)", ")", "KEY")
                    errorAt("CREATE TABLE t (a INT,)", ")", "identifier")
                    errorAt("CREATE TABLE t (PRIMARY KEY)", ")", "'('")
                    val emptyKey = "CREATE TABLE t (PRIMARY KEY ())"
                    errorAtOffset(emptyKey, emptyKey.indexOf("()") + 1, "')'", "identifier")
                    errorAt("CREATE TABLE t a INT", "a", "'('")
                    errorAt("CREATE TABLE (a INT)", "(", "identifier")
                    errorAt("CREATE TABLE t (from INT)", "from", "identifier")
                    errorAt("CREATE TABLE t (column INT)", "column", "identifier")
                }
            }

            `when`("ALTER or DROP is incomplete") {
                then("each case is named") {
                    errorAt("ALTER t ADD c INT", "t", "TABLE")
                    errorAtEnd("ALTER TABLE", "identifier")
                    errorAtEnd("ALTER TABLE t ADD", "identifier")
                    errorAtEnd("ALTER TABLE t DROP", "identifier")
                    errorAtEnd("DROP TABLE", "identifier")
                    errorAtEnd("DROP INDEX", "identifier")
                }
            }
        }

        given("statements that stop at the first keyword") {
            `when`("only the opening keyword is written") {
                then("the error says what should come next") {
                    errorAtEnd("SELECT", "an expression")
                    errorAtEnd("INSERT", "INTO")
                    errorAtEnd("UPDATE", "identifier")
                    errorAtEnd("DELETE", "FROM")
                    errorAtEnd("CREATE", "TABLE, INDEX or UNIQUE")
                    errorAtEnd("DROP", "TABLE or INDEX")
                    errorAtEnd("ALTER", "TABLE")
                }
            }

            `when`("there is nothing to parse") {
                then("an empty, blank or comment-only input is a missing statement, and a lone semicolon is not one") {
                    errorAtEnd("", "a statement")
                    errorAtEnd("   ", "a statement")
                    errorAtEnd("-- nothing here", "a statement")
                    errorAt(";", ";", "a statement")
                }
            }
        }

        given("text left over after a statement") {
            `when`("a second semicolon or a stray token follows") {
                then("only one optional semicolon is allowed") {
                    errorAt("SELECT * FROM t; ;", ";", "end of input")
                    errorAt("SELECT * FROM t WHERE a = 1 b = 2", "b", "end of input")
                }
            }
        }

        given("lexer errors inside a statement") {
            `when`("the text cannot even be split into tokens") {
                then("the lexer's own error comes through with its position") {
                    errorWithCode("SELECT 'abc", ErrorCode.UNTERMINATED_STRING, 7)
                    errorWithCode("SELECT 1 @ 2", ErrorCode.UNEXPECTED_CHARACTER, 9)
                    errorWithCode("INSERT INTO t VALUES (?)", ErrorCode.UNEXPECTED_CHARACTER, 22)
                    errorWithCode("SELECT \"\" FROM t", ErrorCode.EMPTY_QUOTED_IDENTIFIER, 7)
                    errorWithCode("CREATE TABLE \"\" (a INT)", ErrorCode.EMPTY_QUOTED_IDENTIFIER, 13)
                }
            }
        }

        given("syntax this SQL subset does not support") {
            `when`("aliases, qualified names, function calls, joins or type lengths are used") {
                then("each is rejected where the grammar stops understanding it") {
                    errorAt("SELECT a AS x FROM t", "AS", "FROM")
                    errorAt("SELECT t.a FROM t", ".", "FROM")
                    errorAt("SELECT COUNT(*) FROM t", "(", "FROM")
                    errorAt("SELECT * FROM a JOIN b", "JOIN", "end of input")
                    errorAt("SELECT DISTINCT a FROM t", "a", "FROM")
                    errorAt("CREATE TABLE t (a VARCHAR(10))", "(", "')'")
                }
            }
        }

        given("nesting right at the limit") {
            fun parenthesized(levels: Int) = "(".repeat(levels) + "1" + ")".repeat(levels)

            `when`("parentheses, NOT or minus repeat 199 times") {
                then("the statement still parses") {
                    where(parenthesized(199) + " = 1")
                    where("NOT ".repeat(199) + "a")
                    where("- ".repeat(199) + "a = 1")
                }
            }

            `when`("they repeat 200 times") {
                then("NESTING_TOO_DEEP is reported") {
                    for (hostile in listOf(parenthesized(200) + " = 1", "NOT ".repeat(200) + "a", "- ".repeat(200) + "a = 1")) {
                        shouldThrowCode<SqlSyntaxException>(ErrorCode.NESTING_TOO_DEEP) {
                            parse("SELECT * FROM t WHERE $hostile")
                        }
                    }
                }
            }

            `when`("deep nesting appears in an IN list or an INSERT value") {
                then("it counts the same way") {
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.NESTING_TOO_DEEP) {
                        parse("SELECT * FROM t WHERE a IN (${parenthesized(300)})")
                    }
                    shouldThrowCode<SqlSyntaxException>(ErrorCode.NESTING_TOO_DEEP) {
                        parse("INSERT INTO t VALUES (${parenthesized(300)})")
                    }
                }
            }
        }
    })
