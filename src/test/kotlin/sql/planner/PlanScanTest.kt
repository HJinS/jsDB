package sql.planner

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
import sql.expr.And
import sql.expr.Predicate
import sql.expr.bind

class PlanScanTest : BehaviorSpec({
    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("a", ColumnType.INT),
            RowColumn("b", ColumnType.INT),
            RowColumn("name", ColumnType.STRING),
        )
    )

    // `name` has no index on purpose: conditions and ORDER BY on it can only be served by Filter / Sort.
    val pk = IndexRow(1L, "pk", "t", null, isPrimary = true, isUnique = true,
        keyColumns = listOf(IndexColumn("id", ColumnType.LONG, descending = false)))
    val ab = IndexRow(2L, "idx_ab", "t", null, isPrimary = false, isUnique = false,
        keyColumns = listOf(
            IndexColumn("a", ColumnType.INT, descending = false),
            IndexColumn("b", ColumnType.INT, descending = false),
        ))

    // idx_ab is listed first, so a primary-key win has to come from chooseBest's rule, not from list order.
    val planner = Planner(schema, listOf(ab, pk))

    /** A bound WHERE (or one term of it), built the same way the real pipeline does. */
    fun pred(condition: String): Predicate =
        bind((SqlParser.parse("select * from t where $condition") as Select).where!!, schema)

    fun scan(where: String?, vararg orderBy: ColumnOrder): Plan =
        planner.planScan(where?.let(::pred), orderBy.toList())

    fun asc(name: String) = ColumnOrder(name, descending = false)
    fun desc(name: String) = ColumnOrder(name, descending = true)

    fun <T> b(vararg v: T, inclusive: Boolean) = Bound(v.toList<Any?>(), inclusive)
    val unbounded = Bound<List<Any?>>(null, true)

    given("no WHERE and no ORDER BY") {
        `when`("planning") {
            then("it is a bare full scan") {
                scan(null) shouldBe FullScan(emptyList())
            }
        }
    }

    given("a WHERE an index fully expresses") {
        `when`("a = 1") {
            then("it is an index scan with no Filter") {
                scan("a = 1") shouldBe IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), emptyList())
            }
        }

        `when`("id = 7") {
            then("the primary index is scanned") {
                scan("id = 7") shouldBe IndexScan("pk", b(7L, inclusive = true), b(7L, inclusive = true), emptyList())
            }
        }
    }

    given("a WHERE with a range") {
        `when`("a = 1 AND b > 5") {
            then("the range narrows the scan and is still re-checked by the Filter") {
                scan("a = 1 AND b > 5") shouldBe Filter(
                    IndexScan("idx_ab", b(1, 5, inclusive = false), b(1, inclusive = true), emptyList()),
                    pred("b > 5"),
                )
            }
        }
    }

    given("a WHERE with terms the index can't use") {
        `when`("one term is on a column without an index") {
            then("it is left for the Filter") {
                scan("a = 1 AND name = 'kim'") shouldBe Filter(
                    IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), emptyList()),
                    pred("name = 'kim'"),
                )
            }
        }

        `when`("several terms are left over") {
            then("they keep their WHERE order and are joined from the left, skipping the consumed one") {
                scan("name = 'x' AND a = 1 AND b > 5 AND id <> 3") shouldBe Filter(
                    IndexScan("idx_ab", b(1, 5, inclusive = false), b(1, inclusive = true), emptyList()),
                    And(And(pred("name = 'x'"), pred("b > 5")), pred("id <> 3")),
                )
            }
        }

        `when`("two indexes pin one column each") {
            then("the primary index wins the tie and the other term is filtered") {
                scan("a = 1 AND id = 7") shouldBe Filter(
                    IndexScan("pk", b(7L, inclusive = true), b(7L, inclusive = true), emptyList()),
                    pred("a = 1"),
                )
            }
        }
    }

    given("a WHERE no index can use") {
        `when`("planning") {
            then("it is a full scan with the whole WHERE as the Filter") {
                for (where in listOf("a = 1 OR a = 2", "a <> 1", "a = null", "NOT (a = 1)", "name = 'kim'", "b = 1")) {
                    io.kotest.assertions.withClue("WHERE $where") {
                        scan(where) shouldBe Filter(FullScan(emptyList()), pred(where))
                    }
                }
            }
        }
    }

    given("an ORDER BY the chosen index produces") {
        `when`("a = 1 ORDER BY b") {
            then("the scan carries the order and there is no Sort") {
                scan("a = 1", asc("b")) shouldBe
                        IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), listOf(asc("b")))
            }
        }

        `when`("the ORDER BY also names the pinned column") {
            then("the scan carries only the free column, still with no Sort") {
                scan("a = 1", asc("a"), asc("b")) shouldBe
                        IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), listOf(asc("b")))
            }
        }

        `when`("there is no WHERE at all") {
            then("an unbounded index scan gives the order, forward or backward") {
                scan(null, desc("id")) shouldBe IndexScan("pk", unbounded, unbounded, listOf(desc("id")))
                scan(null, asc("a")) shouldBe IndexScan("idx_ab", unbounded, unbounded, listOf(asc("a")))
            }
        }
    }

    given("an index picked for its order or its range") {
        `when`("only the ORDER BY can use an index") {
            then("the index is walked unbounded for the order, and the WHERE is all Filter") {
                scan("name = 'x'", asc("id")) shouldBe Filter(
                    IndexScan("pk", unbounded, unbounded, listOf(asc("id"))),
                    pred("name = 'x'"),
                )
            }
        }

        `when`("one index has a range and another only the order") {
            then("the range wins, so the order comes from a Sort") {
                scan("a > 1", asc("id")) shouldBe Sort(
                    Filter(IndexScan("idx_ab", b(1, inclusive = false), unbounded, emptyList()), pred("a > 1")),
                    listOf(asc("id")),
                )
            }
        }

        `when`("the same index has the range and gives the order backward") {
            then("one backward scan does both, with no Sort") {
                scan("id > 5", desc("id")) shouldBe Filter(
                    IndexScan("pk", b(5L, inclusive = false), unbounded, listOf(desc("id"))),
                    pred("id > 5"),
                )
            }
        }
    }

    given("an ORDER BY the scan can't produce") {
        `when`("the chosen index doesn't give that order") {
            then("the scan gets no order and a Sort with the original ORDER BY wraps it") {
                scan("a = 1", asc("name")) shouldBe Sort(
                    IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), emptyList()),
                    listOf(asc("name")),
                )
            }
        }

        `when`("there is also a Filter") {
            then("the Sort goes outside the Filter, so only surviving rows are sorted") {
                scan("a = 1 AND name = 'x'", desc("name")) shouldBe Sort(
                    Filter(
                        IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), emptyList()),
                        pred("name = 'x'"),
                    ),
                    listOf(desc("name")),
                )
            }
        }

        `when`("no index is used at all") {
            then("it is a full scan, then the Filter, then the Sort") {
                scan(null, asc("name")) shouldBe Sort(FullScan(emptyList()), listOf(asc("name")))
                scan("name = 'x'", asc("b")) shouldBe Sort(Filter(FullScan(emptyList()), pred("name = 'x'")), listOf(asc("b")))
            }
        }

        `when`("the ORDER BY keeps the pinned column but the free part can't be produced") {
            then("the Sort uses the original ORDER BY, pinned column included") {
                scan("a = 1", asc("a"), asc("name")) shouldBe Sort(
                    IndexScan("idx_ab", b(1, inclusive = true), b(1, inclusive = true), emptyList()),
                    listOf(asc("a"), asc("name")),
                )
            }
        }
    }
})
