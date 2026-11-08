package sql.expr

import exception.DatabaseException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import util.ErrorCode

class TypeRulesTest : BehaviorSpec({
    given("compareSqlValues on integers") {
        `when`("the classes differ but the numbers are whole") {
            then("they compare by value") {
                compareSqlValues(5, 5L) shouldBe 0
                compareSqlValues(5.toByte(), 5.toShort()) shouldBe 0
                compareSqlValues(3, 5L) shouldBeLessThan 0
                compareSqlValues(7L, 5) shouldBeGreaterThan 0
            }
        }

        `when`("the values are near the Long limits") {
            then("no precision is lost") {
                compareSqlValues(Long.MAX_VALUE, Long.MAX_VALUE - 1) shouldBeGreaterThan 0
                compareSqlValues(Long.MIN_VALUE, Long.MIN_VALUE + 1) shouldBeLessThan 0
            }
        }
    }

    given("compareSqlValues on decimals") {
        `when`("a whole number meets a fractional one") {
            then("the comparison is exact") {
                compareSqlValues(20, BigDecimal("20.5")) shouldBeLessThan 0
                compareSqlValues(21, BigDecimal("20.5")) shouldBeGreaterThan 0
                compareSqlValues(2L, 2.0) shouldBe 0
            }
        }

        `when`("a float or double meets the decimal written as a literal") {
            then("0.1 equals 0.1 regardless of the binary representation") {
                compareSqlValues(0.1f, BigDecimal("0.1")) shouldBe 0
                compareSqlValues(0.1, BigDecimal("0.1")) shouldBe 0
            }
        }

        `when`("two decimals differ only in scale") {
            then("they are equal") {
                compareSqlValues(BigDecimal("2.0"), BigDecimal("2.00")) shouldBe 0
            }
        }
    }

    given("compareSqlValues on strings and booleans") {
        `when`("comparing strings") {
            then("they order lexicographically and are case-sensitive") {
                compareSqlValues("a", "b") shouldBeLessThan 0
                compareSqlValues("b", "a") shouldBeGreaterThan 0
                compareSqlValues("kim", "kim") shouldBe 0
                compareSqlValues("Kim", "kim") shouldBeLessThan 0
            }
        }

        `when`("comparing booleans") {
            then("false sorts before true") {
                compareSqlValues(false, true) shouldBeLessThan 0
                compareSqlValues(true, true) shouldBe 0
            }
        }
    }

    given("compareSqlValues on values of different kinds") {
        `when`("the binder should have rejected the pair") {
            then("it fails loudly instead of answering") {
                shouldThrowCode<DatabaseException>(ErrorCode.UNEXPECTED) { compareSqlValues("1", 1) }
                shouldThrowCode<DatabaseException>(ErrorCode.UNEXPECTED) { compareSqlValues(true, 1) }
            }
        }
    }
})
