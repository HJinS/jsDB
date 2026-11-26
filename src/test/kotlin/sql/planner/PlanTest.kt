package sql.planner

import exception.DatabaseException
import helper.shouldThrowCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import schema.Bound
import schema.ColumnOrder
import schema.ColumnType
import schema.IndexColumn
import schema.IndexRow
import schema.RowColumn
import schema.RowSchema
import sql.SqlParser
import sql.ast.Select
import sql.expr.Predicate
import sql.expr.bind
import util.ErrorCode

class PlanTest : BehaviorSpec({
    // Same table as PlanScanTest: `name` has no index.
    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("a", ColumnType.INT),
            RowColumn("b", ColumnType.INT),
            RowColumn("name", ColumnType.STRING),
        )
    )
    val pk = IndexRow(1L, "pk", "t", null, isPrimary = true, isUnique = true,
        keyColumns = listOf(IndexColumn("id", ColumnType.LONG, descending = false)))
    val ab = IndexRow(2L, "idx_ab", "t", null, isPrimary = false, isUnique = false,
        keyColumns = listOf(
            IndexColumn("a", ColumnType.INT, descending = false),
            IndexColumn("b", ColumnType.INT, descending = false),
        ))
    val planner = Planner(schema, listOf(ab, pk))

    /** Parses [sql], binds its WHERE the way the real pipeline does, and plans it. */
    fun plan(sql: String): Plan {
        val select = SqlParser.parse(sql) as Select
        return planner.plan(select, select.where?.let { bind(it, schema) })
    }

    fun pred(condition: String): Predicate =
        bind((SqlParser.parse("select * from t where $condition") as Select).where!!, schema)

    fun asc(name: String) = ColumnOrder(name, descending = false)
    fun desc(name: String) = ColumnOrder(name, descending = true)
    fun <T> b(vararg v: T, inclusive: Boolean) = Bound(v.toList<Any?>(), inclusive)
    val unbounded = Bound<List<Any?>>(null, true)

    val aIs1 = IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), emptyList())

    given("no ORDER BY, LIMIT or OFFSET") {
        `when`("planning") {
            then("it is exactly planScan's plan") {
                plan("select * from t") shouldBe FullScan(emptyList())
                plan("select * from t where a = 1") shouldBe aIs1
                plan("select * from t where a = 1 and name = 'x'") shouldBe Filter(aIs1, pred("name = 'x'"))
            }
        }

        `when`("the column list names columns") {
            then("the plan doesn't change: projection is not the planner's job") {
                plan("select name, b from t where a = 1") shouldBe aIs1
            }
        }
    }

    given("an ORDER BY") {
        `when`("an index can produce it") {
            then("each item becomes a ColumnOrder and reaches the scan, direction included") {
                plan("select * from t where a = 1 order by b desc") shouldBe
                        IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), listOf(desc("b")))
                plan("select * from t order by a, b") shouldBe
                        IndexScan("idx_ab", unbounded, unbounded, listOf(asc("a"), asc("b")))
            }
        }

        `when`("no index can produce it") {
            then("the items become the Sort's order, in the order written") {
                plan("select * from t where a = 1 order by name desc, id") shouldBe
                        Sort(aIs1, listOf(desc("name"), asc("id")))
            }
        }

        `when`("it names a column the table doesn't have") {
            then("it throws UNDEFINED_COLUMN") {
                shouldThrowCode<DatabaseException>(ErrorCode.UNDEFINED_COLUMN) {
                    plan("select * from t order by nope")
                }
                shouldThrowCode<DatabaseException>(ErrorCode.UNDEFINED_COLUMN) {
                    plan("select * from t where a = 1 order by b, nope")
                }
            }
        }
    }

    given("a LIMIT or an OFFSET") {
        `when`("only LIMIT is set") {
            then("a Limit wraps the scan and the offset stays null") {
                plan("select * from t where a = 1 limit 10") shouldBe Limit(aIs1, 10, null)
            }
        }

        `when`("only OFFSET is set") {
            then("a Limit wraps the scan and the limit stays null") {
                plan("select * from t where a = 1 offset 5") shouldBe Limit(aIs1, null, 5)
            }
        }

        `when`("both are 0") {
            then("they still count as set") {
                plan("select * from t limit 0") shouldBe Limit(FullScan(emptyList()), 0, null)
                plan("select * from t offset 0") shouldBe Limit(FullScan(emptyList()), null, 0)
            }
        }

        `when`("there is also a Filter and a Sort") {
            then("the Limit is the outermost node, so rows are cut after sorting") {
                plan("select * from t where a = 1 and name <> 'x' order by name limit 10 offset 5") shouldBe Limit(
                    Sort(Filter(aIs1, pred("name <> 'x'")), listOf(asc("name"))),
                    10,
                    5,
                )
            }
        }

        `when`("the ORDER BY is served by the index") {
            then("the Limit wraps the index scan directly") {
                plan("select * from t order by id desc limit 3") shouldBe
                        Limit(IndexScan("pk", unbounded, unbounded, listOf(desc("id"))), 3, null)
            }
        }

        `when`("a value is larger than an Int") {
            then("it becomes Int.MAX_VALUE") {
                plan("select * from t limit 9999999999 offset 9999999999") shouldBe
                        Limit(FullScan(emptyList()), Int.MAX_VALUE, Int.MAX_VALUE)
            }
        }

        `when`("a value is exactly Int.MAX_VALUE") {
            then("it is kept as is") {
                plan("select * from t limit ${Int.MAX_VALUE}") shouldBe
                        Limit(FullScan(emptyList()), Int.MAX_VALUE, null)
            }
        }

        `when`("LIMIT is negative") {
            then("it throws NEGATIVE_LIMIT") {
                shouldThrowCode<DatabaseException>(ErrorCode.NEGATIVE_LIMIT) {
                    plan("select * from t limit -1")
                }
            }
        }

        `when`("OFFSET is negative") {
            then("it throws NEGATIVE_OFFSET") {
                shouldThrowCode<DatabaseException>(ErrorCode.NEGATIVE_OFFSET) {
                    plan("select * from t limit 10 offset -1")
                }
            }
        }
    }
})
