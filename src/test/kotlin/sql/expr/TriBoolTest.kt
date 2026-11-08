package sql.expr

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import sql.expr.TriBool.FALSE
import sql.expr.TriBool.TRUE
import sql.expr.TriBool.UNKNOWN

class TriBoolTest : BehaviorSpec({
    given("TriBool.and") {
        `when`("combining every pair of values") {
            then("FALSE wins, TRUE needs both sides, anything else is UNKNOWN") {
                val expected = mapOf(
                    (TRUE to TRUE) to TRUE,
                    (TRUE to FALSE) to FALSE,
                    (TRUE to UNKNOWN) to UNKNOWN,
                    (FALSE to TRUE) to FALSE,
                    (FALSE to FALSE) to FALSE,
                    (FALSE to UNKNOWN) to FALSE,
                    (UNKNOWN to TRUE) to UNKNOWN,
                    (UNKNOWN to FALSE) to FALSE,
                    (UNKNOWN to UNKNOWN) to UNKNOWN,
                )
                expected.size shouldBe 9
                for ((operands, result) in expected) {
                    (operands.first and operands.second) shouldBe result
                }
            }
        }
    }

    given("TriBool.or") {
        `when`("combining every pair of values") {
            then("TRUE wins, FALSE needs both sides, anything else is UNKNOWN") {
                val expected = mapOf(
                    (TRUE to TRUE) to TRUE,
                    (TRUE to FALSE) to TRUE,
                    (TRUE to UNKNOWN) to TRUE,
                    (FALSE to TRUE) to TRUE,
                    (FALSE to FALSE) to FALSE,
                    (FALSE to UNKNOWN) to UNKNOWN,
                    (UNKNOWN to TRUE) to TRUE,
                    (UNKNOWN to FALSE) to UNKNOWN,
                    (UNKNOWN to UNKNOWN) to UNKNOWN,
                )
                expected.size shouldBe 9
                for ((operands, result) in expected) {
                    (operands.first or operands.second) shouldBe result
                }
            }
        }
    }

    given("TriBool.not") {
        `when`("negating each value") {
            then("TRUE and FALSE swap and UNKNOWN stays UNKNOWN") {
                !TRUE shouldBe FALSE
                !FALSE shouldBe TRUE
                !UNKNOWN shouldBe UNKNOWN
            }
        }
    }

    given("TriBool.isTrue") {
        `when`("deciding whether a row passes a WHERE") {
            then("only TRUE passes") {
                TRUE.isTrue shouldBe true
                FALSE.isTrue shouldBe false
                UNKNOWN.isTrue shouldBe false
            }
        }
    }
})
