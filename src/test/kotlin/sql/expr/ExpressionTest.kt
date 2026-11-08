package sql.expr

import exception.DatabaseException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import schema.ColumnType
import schema.Row
import schema.RowColumn
import schema.RowSchema
import sql.expr.TriBool.FALSE
import sql.expr.TriBool.TRUE
import sql.expr.TriBool.UNKNOWN
import util.ErrorCode

class ExpressionTest : BehaviorSpec({
    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("age", ColumnType.INT),
        )
    )

    fun row(age: Int?) = Row(schema, listOf(1L, age))

    fun lit(value: Any?) = LiteralExpr(value)
    fun yes() = Comparison(CompareOp.EQ, lit(1L), lit(1L))
    fun no() = Comparison(CompareOp.EQ, lit(1L), lit(2L))
    fun unknown() = Comparison(CompareOp.EQ, lit(1L), lit(null))

    given("LiteralExpr") {
        `when`("reading its type") {
            then("it follows the kind of value, and NULL has none") {
                lit(1L).type shouldBe ColumnType.LONG
                lit(BigDecimal("1.5")).type shouldBe ColumnType.DOUBLE
                lit("a").type shouldBe ColumnType.STRING
                lit(true).type shouldBe ColumnType.BOOLEAN
                lit(null).type shouldBe null
            }
        }

        `when`("the value is of a kind the parser never produces") {
            then("reading its type fails") {
                shouldThrowCode<DatabaseException>(ErrorCode.UNEXPECTED) { lit(1).type }
            }
        }

        `when`("evaluating") {
            then("it ignores the row") {
                lit(7L).eval(row(30)) shouldBe 7L
                lit(null).eval(row(30)) shouldBe null
            }
        }
    }

    given("ColumnExpr") {
        `when`("evaluating") {
            then("it reads its own position from the row") {
                ColumnExpr(0, ColumnType.LONG).eval(row(30)) shouldBe 1L
                ColumnExpr(1, ColumnType.INT).eval(row(30)) shouldBe 30
                ColumnExpr(1, ColumnType.INT).eval(row(null)) shouldBe null
            }
        }
    }

    given("Comparison") {
        `when`("both sides have values") {
            then("each operator applies to the three possible orderings") {
                fun cmp(op: CompareOp, l: Long, r: Long) = Comparison(op, lit(l), lit(r)).test(row(0))

                // (less, equal, greater) results of l <op> r for l=1,2,3 against r=2
                val expected = mapOf(
                    CompareOp.EQ to listOf(FALSE, TRUE, FALSE),
                    CompareOp.NE to listOf(TRUE, FALSE, TRUE),
                    CompareOp.LT to listOf(TRUE, FALSE, FALSE),
                    CompareOp.LE to listOf(TRUE, TRUE, FALSE),
                    CompareOp.GT to listOf(FALSE, FALSE, TRUE),
                    CompareOp.GE to listOf(FALSE, TRUE, TRUE),
                )
                for ((op, results) in expected) {
                    listOf(1L, 2L, 3L).map { cmp(op, it, 2L) } shouldBe results
                }
            }
        }

        `when`("either side is NULL") {
            then("every operator yields UNKNOWN, even NULL = NULL and NULL <> NULL") {
                for (op in CompareOp.entries) {
                    Comparison(op, lit(null), lit(1L)).test(row(0)) shouldBe UNKNOWN
                    Comparison(op, lit(1L), lit(null)).test(row(0)) shouldBe UNKNOWN
                    Comparison(op, lit(null), lit(null)).test(row(0)) shouldBe UNKNOWN
                }
            }
        }

        `when`("comparing a column of one numeric type with a literal of another") {
            then("the values are compared as numbers") {
                Comparison(CompareOp.EQ, ColumnExpr(1, ColumnType.INT), lit(30L)).test(row(30)) shouldBe TRUE
                Comparison(
                    CompareOp.GT,
                    ColumnExpr(1, ColumnType.INT),
                    lit(BigDecimal("29.5"))
                ).test(row(30)) shouldBe TRUE
            }
        }
    }

    given("IsNullPredicate") {
        `when`("testing IS NULL and IS NOT NULL") {
            then("it answers TRUE or FALSE and never UNKNOWN") {
                val age = ColumnExpr(1, ColumnType.INT)
                IsNullPredicate(age, false).test(row(null)) shouldBe TRUE
                IsNullPredicate(age, false).test(row(30)) shouldBe FALSE
                IsNullPredicate(age, true).test(row(null)) shouldBe FALSE
                IsNullPredicate(age, true).test(row(30)) shouldBe TRUE
            }
        }
    }

    given("And, Or and Not") {
        `when`("combining conditions") {
            then("they follow the three-valued truth tables") {
                And(yes(), no()).test(row(0)) shouldBe FALSE
                And(yes(), unknown()).test(row(0)) shouldBe UNKNOWN
                And(no(), unknown()).test(row(0)) shouldBe FALSE
                Or(no(), yes()).test(row(0)) shouldBe TRUE
                Or(no(), unknown()).test(row(0)) shouldBe UNKNOWN
                Or(yes(), unknown()).test(row(0)) shouldBe TRUE
                Not(yes()).test(row(0)) shouldBe FALSE
                Not(no()).test(row(0)) shouldBe TRUE
                Not(unknown()).test(row(0)) shouldBe UNKNOWN
            }
        }
    }
})
