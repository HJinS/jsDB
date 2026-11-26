package sql.planner

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import schema.ColumnType
import schema.RowColumn
import schema.RowSchema
import sql.SqlParser
import sql.ast.Select
import sql.expr.And
import sql.expr.ColumnExpr
import sql.expr.CompareOp
import sql.expr.Predicate
import sql.expr.bind
import java.math.BigDecimal

class ConjunctsTest : BehaviorSpec({
    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("a", ColumnType.INT),
            RowColumn("b", ColumnType.INT),
        )
    )
    val a = ColumnExpr(1, ColumnType.INT)

    fun pred(condition: String): Predicate =
        bind((SqlParser.parse("select * from t where $condition") as Select).where!!, schema)

    val t1 = pred("a = 1")
    val t2 = pred("b > 2")
    val t3 = pred("id <> 3")

    given("conjuncts") {
        `when`("the WHERE is a single term") {
            then("it is the only term") {
                conjuncts(t1) shouldBe listOf(t1)
            }
        }

        `when`("the ANDs are nested either way") {
            then("the terms come out flat, in written order") {
                conjuncts(And(And(t1, t2), t3)) shouldBe listOf(t1, t2, t3)
                conjuncts(And(t1, And(t2, t3))) shouldBe listOf(t1, t2, t3)
                conjuncts(pred("a = 1 AND (b > 2 AND id <> 3)")) shouldBe listOf(t1, t2, t3)
            }
        }

        `when`("an AND sits inside an OR or a NOT") {
            then("it is not split: the OR / NOT is one term") {
                val or = pred("(a = 1 AND b > 2) OR id <> 3")
                conjuncts(or) shouldBe listOf(or)

                val not = pred("NOT (a = 1 AND b > 2)")
                conjuncts(And(not, t3)) shouldBe listOf(not, t3)
            }
        }
    }

    given("andAll") {
        `when`("there are no terms") {
            then("it is null") {
                andAll(emptyList()) shouldBe null
            }
        }

        `when`("there is one term") {
            then("it is that term, not wrapped") {
                andAll(listOf(t1)) shouldBe t1
            }
        }

        `when`("there are several terms") {
            then("they are joined from the left") {
                andAll(listOf(t1, t2)) shouldBe And(t1, t2)
                andAll(listOf(t1, t2, t3)) shouldBe And(And(t1, t2), t3)
            }
        }

        `when`("it undoes conjuncts") {
            then("the result is left-nested whatever the original nesting was") {
                andAll(conjuncts(And(t1, And(t2, t3)))) shouldBe And(And(t1, t2), t3)
                andAll(conjuncts(pred("a = 1 AND b > 2 AND id <> 3"))) shouldBe pred("a = 1 AND b > 2 AND id <> 3")
            }
        }
    }

    given("asColumnCondition") {
        `when`("the column is on the left") {
            then("the operator and the literal are taken as they are") {
                val term = pred("a >= 5")
                term.asColumnCondition() shouldBe ColumnCondition(a, CompareOp.GE, 5L, term)
            }
        }

        `when`("the column is on the right") {
            then("the operator is turned around so the column reads first") {
                for ((written, op) in listOf(
                    "5 < a" to CompareOp.GT, "5 <= a" to CompareOp.GE,
                    "5 > a" to CompareOp.LT, "5 >= a" to CompareOp.LE,
                    "5 = a" to CompareOp.EQ, "5 <> a" to CompareOp.NE,
                )) {
                    io.kotest.assertions.withClue(written) {
                        val term = pred(written)
                        term.asColumnCondition() shouldBe ColumnCondition(a, op, 5L, term)
                    }
                }
            }
        }

        `when`("the literal is a decimal or a negative number") {
            then("the parser's value is kept as is, uncoerced") {
                val decimal = pred("a = 1.5")
                decimal.asColumnCondition()?.value shouldBe BigDecimal("1.5")
                pred("a = -3").asColumnCondition()?.value shouldBe -3L
            }
        }

        `when`("the literal is NULL, on either side") {
            then("there is no condition: `a = NULL` is never TRUE") {
                pred("a = null").asColumnCondition() shouldBe null
                pred("null = a").asColumnCondition() shouldBe null
            }
        }

        `when`("the term is not column-vs-literal") {
            then("there is no condition") {
                for (written in listOf("a = b", "1 = 1", "a IS NULL", "a = 1 OR b = 2", "NOT (a = 1)")) {
                    io.kotest.assertions.withClue(written) {
                        pred(written).asColumnCondition() shouldBe null
                    }
                }
            }
        }

        `when`("a condition is made") {
            then("its source is the very term it came from") {
                val term = pred("7 > a")
                term.asColumnCondition()?.source shouldBe term
            }
        }
    }

    given("CompareOp.flipped") {
        `when`("flipping each operator") {
            then("it is the operator for the swapped operands, not the negation") {
                CompareOp.EQ.flipped() shouldBe CompareOp.EQ
                CompareOp.NE.flipped() shouldBe CompareOp.NE
                CompareOp.LT.flipped() shouldBe CompareOp.GT
                CompareOp.LE.flipped() shouldBe CompareOp.GE
                CompareOp.GT.flipped() shouldBe CompareOp.LT
                CompareOp.GE.flipped() shouldBe CompareOp.LE
            }
        }

        `when`("flipping twice") {
            then("it is back to the start") {
                for (op in CompareOp.entries) op.flipped().flipped() shouldBe op
            }
        }

        `when`("swapping operands and flipping") {
            then("every pair of values compares the same way") {
                for (op in CompareOp.entries) for (cmp in -1..1) {
                    // `x op y` holds for cmp = compare(x, y); `y op' x` sees -cmp.
                    op.flipped().matches(-cmp) shouldBe op.matches(cmp)
                }
            }
        }
    }
})
