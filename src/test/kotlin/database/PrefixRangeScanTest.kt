package database

import config.SimpleConfig
import config.StorageConfig
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import schema.Bound
import schema.ColumnOrder
import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import schema.Row
import schema.RowColumn
import schema.RowSchema
import java.io.File
import kotlin.uuid.Uuid

/**
 * Range scans whose bounds are shorter than the index key (prefix bounds), on a real database - `TableTest` mocks the
 * B-tree, so it can't see how the serialized bounds actually sort. Regression test for #76: on an index like
 * `(a ASC, b DESC)`, a bound of just `[a]` used to be padded with b's NULL byte (0xFF), so it sorted *after* every
 * non-NULL `b` of that group.
 *
 * Every index key here is distinct and the table fits in one leaf, so the duplicate-key seek bug (#77) and the
 * backward sibling-link bug (#78) can't show up.
 */
class PrefixRangeScanTest : BehaviorSpec({
    val dbPath = "test-prefix-range-scan-${Uuid.random()}.db"
    val db = DataBase(SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 100))).apply { initialize() }
    afterSpec { db.close(); File(dbPath).delete() }

    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("a", ColumnType.INT),
            RowColumn("b", ColumnType.INT),
            RowColumn("c", ColumnType.INT),
        )
    )

    // The rows from #76's reproduction, plus one with a NULL `a`.
    val rows: List<Row> = buildList {
        for (id in 1L..9L) add(Row(schema, listOf<Any?>(id, 1, id.toInt(), (id % 2).toInt())))
        add(Row(schema, listOf<Any?>(10L, 1, null, 0)))
        add(Row(schema, listOf<Any?>(11L, 2, 5, 1)))
        add(Row(schema, listOf<Any?>(12L, 0, 5, 0)))
        add(Row(schema, listOf<Any?>(13L, null, 3, 1)))
    }

    fun asc(name: String) = IndexColumn(name, ColumnType.INT, descending = false)
    fun desc(name: String) = IndexColumn(name, ColumnType.INT, descending = true)

    val indexes: Map<String, List<IndexColumn>> = mapOf(
        "idx_a_bdesc" to listOf(asc("a"), desc("b")),          // DESC right after the prefix (#76)
        "idx_adesc_bdesc" to listOf(desc("a"), desc("b")),      // DESC prefix, DESC next
        "idx_a_c_bdesc" to listOf(asc("a"), asc("c"), desc("b")), // DESC last, after a two-column prefix
        "idx_bdesc_a" to listOf(desc("b"), asc("a")),          // DESC first, ASC next
        "idx_a_b" to listOf(asc("a"), asc("b")),               // all ASC
    )

    db.createTable("t", "pk_t", schema)
    for ((name, keyColumns) in indexes) {
        db.createIndex(name, "pk_t", "t", isPrimary = false, isUnique = false, IndexKeySchema(keyColumns))
    }
    val table = db.loadTable("t").also { t -> rows.forEach(t::insertRow) }

    fun ids(rows: List<Row>) = rows.map { it["id"] as Long }
    fun bound(vararg values: Any?, inclusive: Boolean = true) = Bound(values.toList(), inclusive)
    val unbounded = Bound<List<Any?>>(null, true)

    given("the cases from #76, on (a ASC, b DESC)") {
        // Index order: a NULL first, then each `a` group with b descending and b NULL last:
        // 13 | 12 | 9 8 7 6 5 4 3 2 1 10 | 11
        val idx = "idx_a_bdesc"
        val backwardB = listOf(ColumnOrder("b", descending = false))

        `when`("a = 1") {
            then("every a = 1 row comes back, forward or backward") {
                ids(table.selectByRange(idx, bound(1), bound(1), emptyList())) shouldBe listOf(9L, 8, 7, 6, 5, 4, 3, 2, 1, 10)
                ids(table.selectByRange(idx, bound(1), bound(1), backwardB)) shouldBe listOf(10L, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            }
        }

        `when`("a = 1 AND b > 5 (b's lower limit is the upper bound, in byte order)") {
            then("rows with b 9..6") {
                ids(table.selectByRange(idx, bound(1), bound(1, 5, inclusive = false), emptyList())) shouldBe listOf(9L, 8, 7, 6)
                ids(table.selectByRange(idx, bound(1), bound(1, 5, inclusive = false), backwardB)) shouldBe listOf(6L, 7, 8, 9)
            }
        }

        `when`("a = 1 AND b >= 5") {
            then("rows with b 9..5") {
                ids(table.selectByRange(idx, bound(1), bound(1, 5), emptyList())) shouldBe listOf(9L, 8, 7, 6, 5)
            }
        }

        `when`("a < 2") {
            then("everything before the a = 2 group, and nothing of it") {
                ids(table.selectByRange(idx, unbounded, bound(2, inclusive = false), emptyList())) shouldBe
                    listOf(13L, 12, 9, 8, 7, 6, 5, 4, 3, 2, 1, 10)
            }
        }

        `when`("a >= 1") {
            then("the whole a = 1 group and everything after it") {
                ids(table.selectByRange(idx, bound(1), unbounded, emptyList())) shouldBe
                    listOf(9L, 8, 7, 6, 5, 4, 3, 2, 1, 10, 11)
            }
        }
    }

    /**
     * What a scan must return, computed from [rows] without any serializer: the rows inside the bounds in index
     * order. A bound shorter than the key compares only its own columns, so a row that matches all of them is
     * "equal" to the bound - included by an inclusive bound, excluded by an exclusive one.
     */
    fun expected(keyColumns: List<IndexColumn>, lower: Bound<List<Any?>>, upper: Bound<List<Any?>>, backward: Boolean): List<Long> {
        fun compareColumn(x: Any?, y: Any?, column: IndexColumn): Int {
            val ascending = when {
                x == y -> 0
                x == null -> -1
                y == null -> 1
                else -> (x as Int).compareTo(y as Int)
            }
            return if (column.descending) -ascending else ascending
        }

        fun compareToBound(row: Row, values: List<Any?>): Int {
            for ((i, value) in values.withIndex()) {
                val c = compareColumn(row[keyColumns[i].name], value, keyColumns[i])
                if (c != 0) return c
            }
            return 0
        }

        val inRange = rows.filter { row ->
            val lowerOk = lower.value?.let { val c = compareToBound(row, it); if (lower.isInclusive) c >= 0 else c > 0 } ?: true
            val upperOk = upper.value?.let { val c = compareToBound(row, it); if (upper.isInclusive) c <= 0 else c < 0 } ?: true
            lowerOk && upperOk
        }
        val sorted = inRange.sortedWith { x, y -> compareToBound(x, keyColumns.map { y[it.name] }) }
        return ids(if (backward) sorted.reversed() else sorted)
    }

    given("every index shape and every combination of prefix and full bounds") {
        // Bound values: one or two columns of NULL, values that exist, and values that don't.
        val values: List<Any?> = listOf(null, 0, 1, 2, 5)
        val boundValues: List<List<Any?>> = values.map { listOf(it) } + values.flatMap { x -> values.map { y -> listOf(x, y) } }
        val bounds: List<Bound<List<Any?>>> = listOf(unbounded) + boundValues.flatMap { listOf(Bound(it, true), Bound(it, false)) }

        for ((name, keyColumns) in indexes) {
            `when`("scanning $name forward and backward") {
                then("each scan returns the rows in range, in index order") {
                    val failures = mutableListOf<String>()
                    for (lower in bounds) for (upper in bounds) {
                        // Backward needs an ORDER BY on the first free key column, against its declared direction.
                        val pinned = (lower.value ?: emptyList()).zip(upper.value ?: emptyList()).takeWhile { (l, u) -> l == u }.size
                        val free = keyColumns.drop(pinned).firstOrNull()
                        val directions = listOfNotNull(
                            false to emptyList<ColumnOrder>(),
                            free?.let { true to listOf(ColumnOrder(it.name, !it.descending)) },
                        )
                        for ((backward, orderBy) in directions) {
                            val want = expected(keyColumns, lower, upper, backward)
                            val got = ids(table.selectByRange(name, lower, upper, orderBy))
                            if (got != want) {
                                failures += "lower=$lower upper=$upper ${if (backward) "BACKWARD" else "FORWARD"}: expected $want but was $got"
                            }
                        }
                    }
                    withClue("${failures.size} scans were wrong, e.g.\n" + failures.take(15).joinToString("\n")) {
                        failures.size shouldBe 0
                    }
                }
            }
        }
    }
})
