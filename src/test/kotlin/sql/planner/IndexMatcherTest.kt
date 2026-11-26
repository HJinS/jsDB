package sql.planner

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
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

class IndexMatcherTest : BehaviorSpec({
    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("a", ColumnType.INT),
            RowColumn("b", ColumnType.INT),
            RowColumn("c", ColumnType.INT),
            RowColumn("name", ColumnType.STRING),
        )
    )

    fun asc(name: String, type: ColumnType = ColumnType.INT, localeTag: String? = null) =
        IndexColumn(name, type, descending = false, localeTag = localeTag)

    fun desc(name: String, type: ColumnType = ColumnType.INT) = IndexColumn(name, type, descending = true)

    fun indexOf(vararg columns: IndexColumn) = IndexRow(1L, "idx", "t", null, false, false, columns.toList())

    /** Parses [where] and returns its AND terms (in order) alongside the usable column conditions. */
    class Parsed(val terms: List<Predicate>, val conditions: List<ColumnCondition>)

    fun parsed(where: String?): Parsed {
        if (where == null) return Parsed(emptyList(), emptyList())
        val ast = (SqlParser.parse("select * from t where $where") as Select).where!!
        val terms = conjuncts(bind(ast, schema))
        return Parsed(terms, terms.mapNotNull { it.asColumnCondition() })
    }

    fun match(index: IndexRow, where: String?, vararg orderBy: ColumnOrder): IndexMatch? =
        matchIndex(schema, index, parsed(where).conditions, orderBy.toList())

    fun <T> b(vararg v: T, inclusive: Boolean) = Bound(v.toList<Any?>(), inclusive)
    val unbounded = Bound<List<Any?>>(null, true)

    given("an index on (a, b)") {
        val ab = indexOf(asc("a"), asc("b"))

        `when`("a is pinned by =") {
            val m = match(ab, "a = 1")!!
            then("both bounds carry the pinned value, inclusively") {
                m.equalityCount shouldBe 1
                m.rangeColumn shouldBe null
                m.lower shouldBe b(1, inclusive = true)
                m.upper shouldBe b(1, inclusive = true)
            }
            then("the = term is consumed") {
                m.consumed shouldBe setOf(parsed("a = 1").terms[0])
            }
        }

        `when`("a is pinned and b has a lower limit") {
            val p = parsed("a = 1 AND b > 5")
            val m = matchIndex(schema, ab, p.conditions, emptyList())!!
            then("the pinned value is mirrored into the open side") {
                m.equalityCount shouldBe 1
                m.rangeColumn shouldBe "b"
                m.lower shouldBe b(1, 5, inclusive = false)
                m.upper shouldBe b(1, inclusive = true)
            }
            then("only the = term is consumed, so the range term stays in the filter") {
                m.consumed shouldBe setOf(p.terms[0])
            }
        }

        `when`("a is pinned and b has an upper limit") {
            val m = match(ab, "a = 1 AND b <= 9")!!
            then("the limit lands in the upper bound") {
                m.lower shouldBe b(1, inclusive = true)
                m.upper shouldBe b(1, 9, inclusive = true)
            }
        }

        `when`("b is bounded on both sides, as BETWEEN produces") {
            val m = match(ab, "a = 1 AND b BETWEEN 3 AND 6")!!
            then("each limit goes to its own side") {
                m.lower shouldBe b(1, 3, inclusive = true)
                m.upper shouldBe b(1, 6, inclusive = true)
            }
        }

        `when`("the literal is written on the left") {
            then("it bounds the same as the flipped comparison") {
                val flipped = match(ab, "1 = a AND 5 < b")!!
                val plain = match(ab, "a = 1 AND b > 5")!!
                flipped.lower shouldBe plain.lower
                flipped.upper shouldBe plain.upper
                flipped.equalityCount shouldBe plain.equalityCount
                flipped.rangeColumn shouldBe plain.rangeColumn
            }
        }

        `when`("the condition order in the WHERE differs from the key order") {
            then("it makes no difference") {
                match(ab, "b > 5 AND a = 1") shouldBe match(ab, "a = 1 AND b > 5")
            }
        }

        `when`("only the first column has a range") {
            val m = match(ab, "a >= 3 AND a < 8")!!
            then("the range is on a, with no pinned prefix") {
                m.equalityCount shouldBe 0
                m.rangeColumn shouldBe "a"
                m.lower shouldBe b(3, inclusive = true)
                m.upper shouldBe b(8, inclusive = false)
            }
        }

        `when`("a is only bounded on one side") {
            val m = match(ab, "a > 3")!!
            then("the other side is unbounded") {
                m.lower shouldBe b(3, inclusive = false)
                m.upper shouldBe unbounded
            }
        }

        `when`("only the second column has a condition") {
            then("the index can't be used, since the first column is unconstrained") {
                match(ab, "b = 1") shouldBe null
                match(ab, "b > 1") shouldBe null
            }
        }

        `when`("a is pinned twice with different values") {
            val p = parsed("a = 1 AND a = 2")
            val m = matchIndex(schema, ab, p.conditions, emptyList())!!
            then("the first pins, and the second is left for the filter to reject every row") {
                m.lower shouldBe b(1, inclusive = true)
                m.consumed shouldBe setOf(p.terms[0])
            }
        }

        `when`("the range limits meet on the same value") {
            val m = match(ab, "a = 1 AND b >= 4 AND b <= 4")!!
            then("the range is dropped rather than risk Table counting b as pinned") {
                m.rangeColumn shouldBe null
                m.lower shouldBe b(1, inclusive = true)
                m.upper shouldBe b(1, inclusive = true)
            }
        }

        `when`("a condition can't go into a bound") {
            then("it doesn't count: <>, OR, NOT, IS NULL, a NULL literal, an inexact literal") {
                match(ab, "a <> 1") shouldBe null
                match(ab, "a = 1 OR a = 2") shouldBe null
                match(ab, "NOT (a = 1)") shouldBe null
                match(ab, "a IS NULL") shouldBe null
                match(ab, "a = null") shouldBe null
                match(ab, "a = 1.5") shouldBe null
                match(ab, "a = 99999999999") shouldBe null
            }
        }

        `when`("the literal is a decimal with no fraction") {
            then("it is converted to the column's own type") {
                match(ab, "a = 30.0")!!.lower shouldBe b(30, inclusive = true)
            }
        }
    }

    given("an index on (a, b DESC)") {
        val abDesc = indexOf(asc("a"), desc("b"))

        `when`("b has a lower limit") {
            val m = match(abDesc, "a = 1 AND b > 5")!!
            then("it goes to the upper bound, since larger values come first in the index") {
                m.lower shouldBe b(1, inclusive = true)
                m.upper shouldBe b(1, 5, inclusive = false)
            }
        }

        `when`("b has an upper limit") {
            val m = match(abDesc, "a = 1 AND b <= 5")!!
            then("it goes to the lower bound") {
                m.lower shouldBe b(1, 5, inclusive = true)
                m.upper shouldBe b(1, inclusive = true)
            }
        }

        `when`("b is bounded on both sides") {
            val m = match(abDesc, "a = 1 AND b BETWEEN 3 AND 6")!!
            then("the limits swap sides") {
                m.lower shouldBe b(1, 6, inclusive = true)
                m.upper shouldBe b(1, 3, inclusive = true)
            }
        }

        `when`("the range column is the first, ASC, one") {
            then("nothing swaps") {
                match(indexOf(asc("a"), desc("b")), "a > 1")!!.lower shouldBe b(1, inclusive = false)
            }
        }
    }

    given("an index on (a, b, c)") {
        val abc = indexOf(asc("a"), asc("b"), asc("c"))

        `when`("the pinned prefix has a gap") {
            val p = parsed("a = 1 AND c = 3")
            val m = matchIndex(schema, abc, p.conditions, emptyList())!!
            then("the prefix stops at the gap and c is left for the filter") {
                m.equalityCount shouldBe 1
                m.consumed shouldBe setOf(p.terms[0])
                m.lower shouldBe b(1, inclusive = true)
            }
        }

        `when`("all three are pinned") {
            val m = match(abc, "a = 1 AND b = 2 AND c = 3")!!
            then("the bounds hold the whole key") {
                m.equalityCount shouldBe 3
                m.lower shouldBe b(1, 2, 3, inclusive = true)
                m.upper shouldBe b(1, 2, 3, inclusive = true)
                m.consumed shouldHaveSize 3
            }
        }

        `when`("a is pinned, b is pinned, and c has a range") {
            val m = match(abc, "a = 1 AND b = 2 AND c < 7")!!
            then("the range is on the third column") {
                m.rangeColumn shouldBe "c"
                m.lower shouldBe b(1, 2, inclusive = true)
                m.upper shouldBe b(1, 2, 7, inclusive = false)
            }
        }
    }

    given("a string column") {
        `when`("it uses plain byte order") {
            then("= pins it") {
                match(indexOf(asc("name", ColumnType.STRING)), "name = 'kim'")!!.lower shouldBe
                        b("kim", inclusive = true)
            }
        }

        `when`("it is ordered by a locale's collation") {
            then("no bound is built from it") {
                match(indexOf(asc("name", ColumnType.STRING, localeTag = "ko-KR")), "name = 'kim'") shouldBe null
            }
        }
    }

    given("ORDER BY") {
        val ab = indexOf(asc("a"), asc("b"))
        val abMixed = indexOf(asc("a"), desc("b"))

        `when`("there are no conditions but the index gives the order") {
            val m = match(ab, null, ColumnOrder("a", false))!!
            then("the index is usable as an unbounded ordered scan") {
                m.equalityCount shouldBe 0
                m.lower shouldBe unbounded
                m.upper shouldBe unbounded
                m.satisfiesOrderBy shouldBe true
                m.scanOrderBy shouldBe listOf(ColumnOrder("a", false))
            }
        }

        `when`("there are no conditions and the ORDER BY is the exact reverse") {
            val m = match(abMixed, null, ColumnOrder("a", true), ColumnOrder("b", false))!!
            then("a backward walk satisfies it") {
                m.satisfiesOrderBy shouldBe true
            }
        }

        `when`("there are no conditions and the directions are a mix of declared and reversed") {
            then("the index can't give the order, so it isn't worth using") {
                match(abMixed, null, ColumnOrder("a", false), ColumnOrder("b", false)) shouldBe null
            }
        }

        `when`("there are no conditions and the ORDER BY skips the first key column") {
            then("it isn't worth using") {
                match(ab, null, ColumnOrder("b", false)) shouldBe null
            }
        }

        `when`("there are no conditions and no ORDER BY") {
            then("the index has nothing to offer") {
                match(ab, null) shouldBe null
            }
        }

        `when`("the ORDER BY names a column that is pinned by =") {
            val m = match(ab, "a = 1", ColumnOrder("a", false), ColumnOrder("b", false))!!
            then("the pinned column is dropped, as Table only matches the free columns") {
                m.satisfiesOrderBy shouldBe true
                m.scanOrderBy shouldBe listOf(ColumnOrder("b", false))
            }
        }

        `when`("the conditions narrow the scan but the ORDER BY can't be satisfied") {
            val m = match(abMixed, "a > 0", ColumnOrder("a", false), ColumnOrder("b", false))!!
            then("the index is still used, but the scan gets no ORDER BY and a Sort must follow") {
                m.satisfiesOrderBy shouldBe false
                m.scanOrderBy shouldBe emptyList()
            }
        }

        `when`("the ORDER BY is longer than the free columns") {
            then("it is not satisfied") {
                satisfiesOrder(ab.keyColumns, listOf(ColumnOrder("a", false), ColumnOrder("b", false)), 1) shouldBe false
            }
        }
    }

    given("satisfiesOrder") {
        val keys = listOf(asc("a"), desc("b"), asc("c"))

        `when`("called with an empty ORDER BY") {
            then("it is satisfied") { satisfiesOrder(keys, emptyList(), 0) shouldBe true }
        }

        `when`("the ORDER BY is a prefix of the free columns in declared direction") {
            then("it is satisfied") {
                satisfiesOrder(keys, listOf(ColumnOrder("a", false)), 0) shouldBe true
                satisfiesOrder(keys, listOf(ColumnOrder("a", false), ColumnOrder("b", true)), 0) shouldBe true
            }
        }

        `when`("the ORDER BY is the full reverse") {
            then("it is satisfied") {
                satisfiesOrder(keys, listOf(ColumnOrder("a", true), ColumnOrder("b", false)), 0) shouldBe true
            }
        }

        `when`("the ORDER BY mixes declared and reversed directions") {
            then("it is not satisfied") {
                satisfiesOrder(keys, listOf(ColumnOrder("a", false), ColumnOrder("b", false)), 0) shouldBe false
            }
        }

        `when`("the ORDER BY has a gap or the wrong order") {
            then("it is not satisfied") {
                satisfiesOrder(keys, listOf(ColumnOrder("b", true)), 0) shouldBe false
                satisfiesOrder(keys, listOf(ColumnOrder("b", true), ColumnOrder("a", false)), 0) shouldBe false
                satisfiesOrder(keys, listOf(ColumnOrder("a", false), ColumnOrder("c", false)), 0) shouldBe false
            }
        }

        `when`("leading columns are pinned") {
            then("the free columns start after them") {
                satisfiesOrder(keys, listOf(ColumnOrder("b", true)), 1) shouldBe true
                satisfiesOrder(keys, listOf(ColumnOrder("c", false)), 2) shouldBe true
                satisfiesOrder(keys, listOf(ColumnOrder("a", false)), 1) shouldBe false
            }
        }
    }
})
