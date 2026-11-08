package sql.expr

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import schema.ColumnType
import schema.Row
import schema.RowColumn
import schema.RowSchema
import sql.SqlParser
import sql.ast.Select
import sql.expr.TriBool.FALSE
import sql.expr.TriBool.TRUE
import sql.expr.TriBool.UNKNOWN

/** SQL text -> parse -> bind -> evaluate against real rows. */
class PredicateEvaluationTest : BehaviorSpec({
    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("age", ColumnType.INT),
            RowColumn("name", ColumnType.STRING),
            RowColumn("score", ColumnType.DOUBLE),
            RowColumn("flag", ColumnType.BOOLEAN),
        )
    )

    fun row(age: Int? = 30, name: String? = "kim", score: Double? = 1.5, flag: Boolean? = true) =
        Row(schema, listOf(1L, age, name, score, flag))

    fun eval(condition: String, row: Row): TriBool {
        val where = (SqlParser.parse("select * from t where $condition") as Select).where!!
        return bind(where, schema).test(row)
    }

    fun check(row: Row, cases: Map<String, TriBool>) {
        for ((condition, expected) in cases) {
            withClue("WHERE $condition") { eval(condition, row) shouldBe expected }
        }
    }

    given("a row with every column set") {
        val full = row()

        `when`("comparing numbers") {
            then("all six operators work, across integer and decimal kinds") {
                check(
                    full,
                    mapOf(
                        "age = 30" to TRUE,
                        "age = 31" to FALSE,
                        "age <> 30" to FALSE,
                        "age > 20" to TRUE,
                        "age >= 30" to TRUE,
                        "age < 30" to FALSE,
                        "age <= 30" to TRUE,
                        "age = 30.0" to TRUE,
                        "age > 29.5" to TRUE,
                        "score > 1" to TRUE,
                        "score = 1.5" to TRUE,
                        "id = 1" to TRUE,
                        "id = age" to FALSE,
                        "20 < age" to TRUE,
                    )
                )
            }
        }

        `when`("comparing strings and booleans") {
            then("they compare by value") {
                check(
                    full,
                    mapOf(
                        "name = 'kim'" to TRUE,
                        "name = 'Kim'" to FALSE,
                        "name < 'lee'" to TRUE,
                        "flag = true" to TRUE,
                        "flag = false" to FALSE,
                    )
                )
            }
        }

        `when`("combining conditions") {
            then("AND, OR and NOT behave as in two-valued logic") {
                check(
                    full,
                    mapOf(
                        "age > 20 AND name = 'kim'" to TRUE,
                        "age > 20 AND name = 'lee'" to FALSE,
                        "age > 40 OR name = 'kim'" to TRUE,
                        "age > 40 OR name = 'lee'" to FALSE,
                        "NOT (age > 40)" to TRUE,
                        "NOT (age > 20)" to FALSE,
                    )
                )
            }
        }

        `when`("using BETWEEN and IN") {
            then("BETWEEN is inclusive and NOT flips both") {
                check(
                    full,
                    mapOf(
                        "age BETWEEN 20 AND 30" to TRUE,
                        "age BETWEEN 30 AND 40" to TRUE,
                        "age BETWEEN 31 AND 40" to FALSE,
                        "age NOT BETWEEN 31 AND 40" to TRUE,
                        "age NOT BETWEEN 20 AND 30" to FALSE,
                        "age IN (1, 30)" to TRUE,
                        "age IN (1, 2)" to FALSE,
                        "age NOT IN (1, 2)" to TRUE,
                        "age NOT IN (1, 30)" to FALSE,
                    )
                )
            }
        }

        `when`("testing IS NULL") {
            then("a present value is not null") {
                check(full, mapOf("age IS NULL" to FALSE, "age IS NOT NULL" to TRUE))
            }
        }

        `when`("comparing with a NULL literal") {
            then("the result is UNKNOWN, never TRUE") {
                check(full, mapOf("age = null" to UNKNOWN, "age <> null" to UNKNOWN, "NOT (age = null)" to UNKNOWN))
            }
        }
    }

    given("a row with a negative value") {
        val negative = row(age = -5, score = -1.5)

        `when`("comparing against negative literals") {
            then("the sign is respected") {
                check(
                    negative,
                    mapOf(
                        "age = -5" to TRUE,
                        "age = 5" to FALSE,
                        "age < -1" to TRUE,
                        "age > -10" to TRUE,
                        "age BETWEEN -10 AND -1" to TRUE,
                        "age BETWEEN -4 AND 4" to FALSE,
                        "age IN (-5, 5)" to TRUE,
                        "score = -1.5" to TRUE,
                        "score < -1" to TRUE,
                        "-5 = age" to TRUE,
                    )
                )
            }
        }
    }

    given("a row where age is NULL") {
        val nullAge = row(age = null)

        `when`("comparing the NULL column") {
            then("every comparison is UNKNOWN, including its negation") {
                check(
                    nullAge,
                    mapOf(
                        "age = 1" to UNKNOWN,
                        "age <> 1" to UNKNOWN,
                        "age > 1" to UNKNOWN,
                        "age = age" to UNKNOWN,
                        "NOT (age = 1)" to UNKNOWN,
                    )
                )
            }
        }

        `when`("testing IS NULL") {
            then("it is the one test that can see a NULL") {
                check(nullAge, mapOf("age IS NULL" to TRUE, "age IS NOT NULL" to FALSE))
            }
        }

        `when`("combining UNKNOWN with known results") {
            then("AND is FALSE with a FALSE side and OR is TRUE with a TRUE side") {
                check(
                    nullAge,
                    mapOf(
                        "age = 1 AND flag = false" to FALSE,
                        "age = 1 AND flag = true" to UNKNOWN,
                        "age = 1 OR flag = true" to TRUE,
                        "age = 1 OR flag = false" to UNKNOWN,
                    )
                )
            }
        }

        `when`("using BETWEEN and IN") {
            then("they are UNKNOWN, and so are their negations") {
                check(
                    nullAge,
                    mapOf(
                        "age BETWEEN 1 AND 100" to UNKNOWN,
                        "age NOT BETWEEN 1 AND 100" to UNKNOWN,
                        "age IN (1, 2)" to UNKNOWN,
                        "age NOT IN (1, 2)" to UNKNOWN,
                    )
                )
            }
        }

        `when`("the row is filtered by a WHERE") {
            then("UNKNOWN rows are dropped just like FALSE ones") {
                eval("age = 1", nullAge).isTrue shouldBe false
                eval("NOT (age = 1)", nullAge).isTrue shouldBe false
            }
        }
    }

    given("an IN list that contains NULL") {
        `when`("the value is in the list") {
            then("IN is TRUE and NOT IN is FALSE") {
                check(row(age = 1), mapOf("age IN (1, null)" to TRUE, "age NOT IN (1, null)" to FALSE))
            }
        }

        `when`("the value is not in the list") {
            then("the NULL makes both IN and NOT IN UNKNOWN") {
                check(row(age = 5), mapOf("age IN (1, null)" to UNKNOWN, "age NOT IN (1, null)" to UNKNOWN))
            }
        }
    }

    given("a row where several columns are NULL") {
        val sparse = row(age = null, name = null, score = null, flag = null)

        `when`("testing each column") {
            then("IS NULL is TRUE and every comparison is UNKNOWN") {
                check(
                    sparse,
                    mapOf(
                        "name IS NULL" to TRUE,
                        "score IS NULL" to TRUE,
                        "flag IS NULL" to TRUE,
                        "name = 'kim'" to UNKNOWN,
                        "score > 1" to UNKNOWN,
                        "flag = true" to UNKNOWN,
                    )
                )
            }
        }
    }
})
