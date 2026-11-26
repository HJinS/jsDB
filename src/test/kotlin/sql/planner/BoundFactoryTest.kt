package sql.planner

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import schema.ColumnType
import schema.IndexColumn
import sql.expr.compareSqlValues

class BoundFactoryTest : BehaviorSpec({
    fun col(type: ColumnType, localeTag: String? = null) = IndexColumn("c", type, false, localeTag)

    fun coerce(value: Any, type: ColumnType, localeTag: String? = null): Any? =
        coerceLiteral(value, col(type, localeTag))

    fun dec(s: String) = BigDecimal(s)

    given("a whole-number column") {
        `when`("the literal is a Long inside the column's range") {
            then("it becomes the column's own JVM type") {
                // shouldBe on boxed values compares the class too, so these assert the type, not just the number.
                coerce(5L, ColumnType.INT) shouldBe 5
                coerce(5L, ColumnType.SHORT) shouldBe 5.toShort()
                coerce(5L, ColumnType.BYTE) shouldBe 5.toByte()
                coerce(5L, ColumnType.LONG) shouldBe 5L
                coerce(-5L, ColumnType.INT) shouldBe -5
            }
        }

        `when`("the literal sits exactly on the range edges") {
            then("both edges are accepted") {
                coerce(Byte.MAX_VALUE.toLong(), ColumnType.BYTE) shouldBe Byte.MAX_VALUE
                coerce(Byte.MIN_VALUE.toLong(), ColumnType.BYTE) shouldBe Byte.MIN_VALUE
                coerce(Short.MAX_VALUE.toLong(), ColumnType.SHORT) shouldBe Short.MAX_VALUE
                coerce(Short.MIN_VALUE.toLong(), ColumnType.SHORT) shouldBe Short.MIN_VALUE
                coerce(Int.MAX_VALUE.toLong(), ColumnType.INT) shouldBe Int.MAX_VALUE
                coerce(Int.MIN_VALUE.toLong(), ColumnType.INT) shouldBe Int.MIN_VALUE
                coerce(Long.MAX_VALUE, ColumnType.LONG) shouldBe Long.MAX_VALUE
                coerce(Long.MIN_VALUE, ColumnType.LONG) shouldBe Long.MIN_VALUE
            }
        }

        `when`("the literal is one past the range") {
            then("it isn't representable") {
                coerce(Byte.MAX_VALUE + 1L, ColumnType.BYTE) shouldBe null
                coerce(Byte.MIN_VALUE - 1L, ColumnType.BYTE) shouldBe null
                coerce(Short.MAX_VALUE + 1L, ColumnType.SHORT) shouldBe null
                coerce(Short.MIN_VALUE - 1L, ColumnType.SHORT) shouldBe null
                coerce(Int.MAX_VALUE + 1L, ColumnType.INT) shouldBe null
                coerce(Int.MIN_VALUE - 1L, ColumnType.INT) shouldBe null
            }
        }

        `when`("the literal is a decimal") {
            then("only a zero fraction converts") {
                coerce(dec("30.0"), ColumnType.INT) shouldBe 30
                coerce(dec("30"), ColumnType.INT) shouldBe 30
                coerce(dec("-2.00"), ColumnType.LONG) shouldBe -2L
                coerce(dec("1.5"), ColumnType.INT) shouldBe null
                coerce(dec("0.0000001"), ColumnType.LONG) shouldBe null
            }
        }

        `when`("a decimal literal is outside the Long range or the narrower column range") {
            then("it isn't representable") {
                coerce(dec("9223372036854775808"), ColumnType.LONG) shouldBe null
                coerce(dec("1E+30"), ColumnType.LONG) shouldBe null
                coerce(dec("300.0"), ColumnType.BYTE) shouldBe null
            }
        }
    }

    given("a DOUBLE column") {
        `when`("the literal is a Long") {
            then("it converts when the double holds it exactly") {
                coerce(1L, ColumnType.DOUBLE) shouldBe 1.0
                coerce(-7L, ColumnType.DOUBLE) shouldBe -7.0
                coerce(1L shl 53, ColumnType.DOUBLE) shouldBe 9007199254740992.0
            }

            then("it is rejected when the double would round it") {
                coerce((1L shl 53) + 1, ColumnType.DOUBLE) shouldBe null
                coerce(Long.MAX_VALUE, ColumnType.DOUBLE) shouldBe null
            }
        }

        `when`("the literal is a decimal") {
            then("it converts when it survives the round trip") {
                coerce(dec("1.5"), ColumnType.DOUBLE) shouldBe 1.5
                coerce(dec("0.1"), ColumnType.DOUBLE) shouldBe 0.1
                coerce(dec("-1.25"), ColumnType.DOUBLE) shouldBe -1.25
            }

            then("it is rejected when it has more precision than a double keeps") {
                coerce(dec("0.10000000000000000001"), ColumnType.DOUBLE) shouldBe null
            }

            then("it is rejected when it overflows to infinity") {
                coerce(dec("1E+400"), ColumnType.DOUBLE) shouldBe null
            }
        }
    }

    given("a FLOAT column") {
        `when`("the literal fits a float") {
            then("it becomes a Float") {
                coerce(1L, ColumnType.FLOAT) shouldBe 1.0f
                coerce(dec("1.5"), ColumnType.FLOAT) shouldBe 1.5f
                coerce(dec("0.1"), ColumnType.FLOAT) shouldBe 0.1f
                coerce(1L shl 24, ColumnType.FLOAT) shouldBe 16777216.0f
            }
        }

        `when`("the literal needs more precision than a float keeps") {
            then("it is rejected") {
                coerce((1L shl 24) + 1, ColumnType.FLOAT) shouldBe null
                coerce(dec("0.123456789"), ColumnType.FLOAT) shouldBe null
                coerce(dec("1E+39"), ColumnType.FLOAT) shouldBe null
            }
        }
    }

    given("a STRING column") {
        `when`("it uses plain byte order") {
            then("a string literal passes through") {
                coerce("kim", ColumnType.STRING) shouldBe "kim"
                coerce("", ColumnType.STRING) shouldBe ""
            }
        }

        `when`("it is ordered by a locale's collation") {
            then("no literal is usable, since the filter compares by String.compareTo") {
                coerce("kim", ColumnType.STRING, localeTag = "ko-KR") shouldBe null
            }
        }
    }

    given("a BOOLEAN column") {
        `when`("the literal is a boolean") {
            then("it passes through") {
                coerce(true, ColumnType.BOOLEAN) shouldBe true
                coerce(false, ColumnType.BOOLEAN) shouldBe false
            }
        }
    }

    given("a literal of the wrong kind for the column") {
        `when`("coercing it") {
            then("it is simply not usable for the index") {
                coerce("abc", ColumnType.INT) shouldBe null
                coerce(true, ColumnType.INT) shouldBe null
                coerce(1L, ColumnType.STRING) shouldBe null
                coerce(1L, ColumnType.BOOLEAN) shouldBe null
                coerce("true", ColumnType.BOOLEAN) shouldBe null
                coerce(dec("1.5"), ColumnType.STRING) shouldBe null
            }
        }
    }

    given("a column type that has no comparison rules yet") {
        `when`("coercing any literal") {
            then("nothing is usable") {
                coerce("2020-01-01", ColumnType.LOCAL_DATE) shouldBe null
                coerce(1L, ColumnType.LOCAL_DATE_TIME) shouldBe null
                coerce(1L, ColumnType.INSTANT) shouldBe null
                coerce("x", ColumnType.UUID) shouldBe null
                coerce("x", ColumnType.BYTES) shouldBe null
            }
        }
    }

    given("the filter and the index must agree") {
        `when`("a literal is coerced") {
            then("the coerced value compares equal to the original literal under compareSqlValues") {
                val literals: List<Any> = listOf(
                    0L, 1L, -1L, 127L, 128L, 40000L, Int.MAX_VALUE + 1L, 1L shl 24, (1L shl 24) + 1,
                    1L shl 53, (1L shl 53) + 1, Long.MAX_VALUE,
                    dec("30.0"), dec("1.5"), dec("-1.5"), dec("0.1"), dec("0.123456789"),
                    dec("0.10000000000000000001"), dec("1E+39"), dec("1E+400"),
                )
                val numericTypes = listOf(
                    ColumnType.BYTE, ColumnType.SHORT, ColumnType.INT, ColumnType.LONG,
                    ColumnType.FLOAT, ColumnType.DOUBLE,
                )
                for (type in numericTypes) {
                    for (literal in literals) {
                        val coerced = coerce(literal, type) ?: continue
                        io.kotest.assertions.withClue("$literal -> $type ($coerced)") {
                            compareSqlValues(coerced, literal) shouldBe 0
                        }
                    }
                }
            }
        }
    }
})
