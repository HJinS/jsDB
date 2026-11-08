package sql.expr

import exception.DatabaseException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import schema.ColumnType
import schema.RowColumn
import schema.RowSchema
import sql.SqlParser
import sql.ast.Expr
import sql.ast.Select
import util.ErrorCode

class BinderTest : BehaviorSpec({
    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("age", ColumnType.INT),
            RowColumn("name", ColumnType.STRING),
            RowColumn("score", ColumnType.DOUBLE),
            RowColumn("flag", ColumnType.BOOLEAN),
            RowColumn("born", ColumnType.LOCAL_DATE),
        )
    )
    val id = ColumnExpr(0, ColumnType.LONG)
    val age = ColumnExpr(1, ColumnType.INT)
    val name = ColumnExpr(2, ColumnType.STRING)
    val flag = ColumnExpr(4, ColumnType.BOOLEAN)

    fun where(condition: String): Expr =
        (SqlParser.parse("select * from t where $condition") as Select).where!!

    fun bound(condition: String): Predicate = bind(where(condition), schema)

    fun mismatch(condition: String) =
        shouldThrowCode<DatabaseException>(ErrorCode.DATATYPE_MISMATCH) { bound(condition) }

    fun unsupported(condition: String) =
        shouldThrowCode<DatabaseException>(ErrorCode.FEATURE_NOT_SUPPORTED) { bound(condition) }

    given("a comparison") {
        `when`("a column is compared with a literal") {
            then("the name becomes its position and type, the literal keeps the parser's value") {
                bound("age > 20") shouldBe Comparison(CompareOp.GT, age, LiteralExpr(20L))
                bound("name = 'kim'") shouldBe Comparison(CompareOp.EQ, name, LiteralExpr("kim"))
                bound("flag = true") shouldBe Comparison(CompareOp.EQ, flag, LiteralExpr(true))
                bound("score <= 1.5") shouldBe Comparison(
                    CompareOp.LE,
                    ColumnExpr(3, ColumnType.DOUBLE),
                    LiteralExpr(BigDecimal("1.5"))
                )
            }
        }

        `when`("every comparison operator is used") {
            then("each maps to its CompareOp") {
                bound("age = 1") shouldBe Comparison(CompareOp.EQ, age, LiteralExpr(1L))
                bound("age <> 1") shouldBe Comparison(CompareOp.NE, age, LiteralExpr(1L))
                bound("age < 1") shouldBe Comparison(CompareOp.LT, age, LiteralExpr(1L))
                bound("age <= 1") shouldBe Comparison(CompareOp.LE, age, LiteralExpr(1L))
                bound("age > 1") shouldBe Comparison(CompareOp.GT, age, LiteralExpr(1L))
                bound("age >= 1") shouldBe Comparison(CompareOp.GE, age, LiteralExpr(1L))
            }
        }

        `when`("the literal is on the left or two columns are compared") {
            then("operand order is preserved") {
                bound("20 < age") shouldBe Comparison(CompareOp.LT, LiteralExpr(20L), age)
                bound("id = age") shouldBe Comparison(CompareOp.EQ, id, age)
            }
        }

        `when`("a literal is NULL") {
            then("it binds against a column of any type") {
                bound("age = null") shouldBe Comparison(CompareOp.EQ, age, LiteralExpr(null))
                bound("name = null") shouldBe Comparison(CompareOp.EQ, name, LiteralExpr(null))
                bound("born = null") shouldBe Comparison(
                    CompareOp.EQ,
                    ColumnExpr(5, ColumnType.LOCAL_DATE),
                    LiteralExpr(null)
                )
            }
        }
    }

    given("logical operators") {
        `when`("combining conditions") {
            then("AND, OR and NOT keep the parser's nesting") {
                bound("age > 1 AND name = 'a'") shouldBe And(
                    Comparison(CompareOp.GT, age, LiteralExpr(1L)),
                    Comparison(CompareOp.EQ, name, LiteralExpr("a"))
                )
                bound("age > 1 OR age < 0") shouldBe Or(
                    Comparison(CompareOp.GT, age, LiteralExpr(1L)),
                    Comparison(CompareOp.LT, age, LiteralExpr(0L))
                )
                bound("NOT (age = 1)") shouldBe Not(Comparison(CompareOp.EQ, age, LiteralExpr(1L)))
                bound("age = 1 OR age = 2 AND name = 'a'") shouldBe Or(
                    Comparison(CompareOp.EQ, age, LiteralExpr(1L)),
                    And(
                        Comparison(CompareOp.EQ, age, LiteralExpr(2L)),
                        Comparison(CompareOp.EQ, name, LiteralExpr("a"))
                    )
                )
            }
        }
    }

    given("IS NULL") {
        `when`("testing a column") {
            then("negation is carried over") {
                bound("age IS NULL") shouldBe IsNullPredicate(age, false)
                bound("age IS NOT NULL") shouldBe IsNullPredicate(age, true)
            }
        }

        `when`("the column has a type that has no comparison rules") {
            then("IS NULL still works, since it never compares") {
                bound("born IS NULL") shouldBe IsNullPredicate(ColumnExpr(5, ColumnType.LOCAL_DATE), false)
            }
        }
    }

    given("BETWEEN") {
        `when`("binding it") {
            then("it becomes a pair of inclusive comparisons") {
                val range = And(
                    Comparison(CompareOp.GE, age, LiteralExpr(10L)),
                    Comparison(CompareOp.LE, age, LiteralExpr(20L))
                )
                bound("age BETWEEN 10 AND 20") shouldBe range
                bound("age NOT BETWEEN 10 AND 20") shouldBe Not(range)
            }
        }

        `when`("a bound is of the wrong type") {
            then("each bound is checked") {
                mismatch("age BETWEEN 'a' AND 20")
                mismatch("age BETWEEN 10 AND 'z'")
            }
        }
    }

    given("IN") {
        `when`("binding a list") {
            then("it becomes equality comparisons chained with OR, in order") {
                val eq = { v: Long -> Comparison(CompareOp.EQ, age, LiteralExpr(v)) }
                bound("age IN (1)") shouldBe eq(1)
                bound("age IN (1, 2, 3)") shouldBe Or(Or(eq(1), eq(2)), eq(3))
                bound("age NOT IN (1, 2)") shouldBe Not(Or(eq(1), eq(2)))
            }
        }

        `when`("one item is of the wrong type") {
            then("it is rejected") {
                mismatch("age IN (1, 'two')")
            }
        }
    }

    given("numeric compatibility") {
        `when`("a column is compared with a literal of another numeric kind") {
            then("integer columns accept decimals and decimal columns accept integers") {
                bound("age > 1.5") shouldBe Comparison(CompareOp.GT, age, LiteralExpr(BigDecimal("1.5")))
                bound("score > 1") shouldBe Comparison(CompareOp.GT, ColumnExpr(3, ColumnType.DOUBLE), LiteralExpr(1L))
                bound("id = age") shouldBe Comparison(CompareOp.EQ, id, age)
            }
        }
    }

    given("negative number literals") {
        `when`("a minus stands in front of a number") {
            then("it is folded into a negative literal") {
                bound("age = -1") shouldBe Comparison(CompareOp.EQ, age, LiteralExpr(-1L))
                bound("-1 < age") shouldBe Comparison(CompareOp.LT, LiteralExpr(-1L), age)
                bound("score > -1.5") shouldBe Comparison(
                    CompareOp.GT,
                    ColumnExpr(3, ColumnType.DOUBLE),
                    LiteralExpr(BigDecimal("-1.5"))
                )
                bound("age = - -1") shouldBe Comparison(CompareOp.EQ, age, LiteralExpr(1L))
            }
        }

        `when`("negative literals appear in BETWEEN and IN") {
            then("each bound and item is folded") {
                bound("age BETWEEN -10 AND -1") shouldBe And(
                    Comparison(CompareOp.GE, age, LiteralExpr(-10L)),
                    Comparison(CompareOp.LE, age, LiteralExpr(-1L)),
                )
                bound("age IN (-1, -2)") shouldBe Or(
                    Comparison(CompareOp.EQ, age, LiteralExpr(-1L)),
                    Comparison(CompareOp.EQ, age, LiteralExpr(-2L)),
                )
            }
        }

        `when`("a negative literal is of the wrong type for the column") {
            then("the type check still applies") {
                mismatch("name = -1")
            }
        }

        `when`("a minus stands in front of something that isn't a number literal") {
            then("it is reported as unsupported") {
                unsupported("age = -age")
                unsupported("age = -'a'")
                unsupported("age = -null")
            }
        }
    }

    given("an unknown column") {
        `when`("it appears anywhere in the condition") {
            then("the schema rejects it") {
                shouldThrowCode<DatabaseException>(ErrorCode.UNDEFINED_COLUMN) { bound("nope = 1") }
                shouldThrowCode<DatabaseException>(ErrorCode.UNDEFINED_COLUMN) { bound("1 = nope") }
                shouldThrowCode<DatabaseException>(ErrorCode.UNDEFINED_COLUMN) { bound("age = 1 AND nope IS NULL") }
                shouldThrowCode<DatabaseException>(ErrorCode.UNDEFINED_COLUMN) { bound("nope BETWEEN 1 AND 2") }
            }
        }
    }

    given("incomparable operands") {
        `when`("the types are unrelated") {
            then("the error names both types") {
                mismatch("age = 'abc'").message shouldBe "[42804/DATATYPE_MISMATCH] Expression: cannot compare INT with STRING"
                mismatch("name = 1")
                mismatch("flag = 1")
                mismatch("age = true")
                mismatch("name = age")
                mismatch("1 = 'a'")
            }
        }

        `when`("a column type has no comparison rules yet") {
            then("it can only be compared with NULL") {
                mismatch("born = 'x'")
                mismatch("born = 1")
                mismatch("born = born")
            }
        }
    }

    given("a value or condition in the wrong place") {
        `when`("the condition is a bare value") {
            then("it is rejected") {
                mismatch("age")
                mismatch("1")
                mismatch("'a'")
                mismatch("flag")
            }
        }

        `when`("an operand of a logical operator is a bare value") {
            then("it is rejected") {
                mismatch("age = 1 AND age")
                mismatch("NOT age")
            }
        }

        `when`("a condition is used as a value") {
            then("it is rejected") {
                mismatch("(age = 1) = (age = 2)")
                mismatch("(age = 1) IS NULL")
            }
        }
    }

    given("features not supported yet") {
        `when`("using LIKE") {
            then("it is reported as unsupported") {
                unsupported("name LIKE 'a%'")
                unsupported("name NOT LIKE 'a%'")
            }
        }

        `when`("using arithmetic or concatenation") {
            then("it is reported as unsupported") {
                unsupported("age + 1 > 2")
                unsupported("age > 1 * 2")
                unsupported("name || 'x' = 'y'")
            }
        }

        `when`("using unary minus") {
            then("it is reported as unsupported") {
                unsupported("-age > 1")
            }
        }
    }

    given("the Binder class") {
        `when`("used directly") {
            then("it gives the same result as bind()") {
                val expr = where("age > 1")
                Binder(schema).bind(expr) shouldBe bind(expr, schema)
            }
        }
    }
})
