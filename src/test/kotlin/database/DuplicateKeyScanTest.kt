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
 * Range scans on non-unique indexes whose keys repeat across leaf pages, on a real database. Regression test for #77:
 * a scan that starts *exactly* on a stored key used to land on whichever duplicate a binary search hit, skipping the
 * ones before it.
 *
 * Scans run both ways: the rows go in out of key order, so leaves split in the middle, and a backward walk also
 * needs every left sibling link to be right (#78).
 */
class DuplicateKeyScanTest : BehaviorSpec({
    val dbPath = "test-duplicate-key-scan-${Uuid.random()}.db"
    val db = DataBase(SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 100))).apply { initialize() }
    afterSpec { db.close(); File(dbPath).delete() }

    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("v", ColumnType.INT),
            RowColumn("w", ColumnType.INT),
        )
    )
    val unbounded = Bound<List<Any?>>(null, true)
    fun ids(rows: List<Row>) = rows.map { it["id"] as Long }

    given("the reproduction from #77: every row has the same key") {
        `when`("scanning for that key") {
            then("every row comes back, however many leaves the duplicates span") {
                for (n in listOf(10, 20, 80, 500)) withClue("$n rows") {
                    val name = "same_$n"
                    db.createTable(name, "pk_$name", schema)
                    db.createIndex(
                        "idx_$name",
                        "pk_$name",
                        name,
                        isPrimary = false,
                        isUnique = false,
                        IndexKeySchema(listOf(IndexColumn("v", ColumnType.INT, descending = false)))
                    )
                    val table = db.loadTable(name).also { t ->
                        for (id in 1L..n) t.insertRow(Row(schema, listOf<Any?>(id, 7, 0)))
                    }

                    val seven = Bound<List<Any?>>(listOf(7), true)
                    ids(table.selectByRange("idx_$name", seven, seven, emptyList())).sorted() shouldBe (1L..n).toList()
                    ids(table.selectByRange("idx_$name", seven, unbounded, emptyList())).size shouldBe n
                }
            }
        }
    }

    given("a table with many repeated keys, NULLs included, and indexes of every direction") {
        // 600 rows, v in {NULL, 0..6} and w in {NULL, 0..2}: each v repeats ~70 times and each (v, w) ~25 times.
        val rows: List<Row> = (1L..600L).map { id ->
            Row(
                schema,
                listOf<Any?>(
                    id,
                    if (id % 9 == 0L) null else (id % 7).toInt(),
                    if (id % 11 == 0L) null else (id % 3).toInt()
                )
            )
        }
        val indexes: Map<String, List<IndexColumn>> = mapOf(
            "idx_v" to listOf(IndexColumn("v", ColumnType.INT, descending = false)),
            "idx_v_desc" to listOf(IndexColumn("v", ColumnType.INT, descending = true)),
            "idx_v_w" to listOf(
                IndexColumn("v", ColumnType.INT, descending = false),
                IndexColumn("w", ColumnType.INT, descending = false)
            ),
            "idx_v_wdesc" to listOf(
                IndexColumn("v", ColumnType.INT, descending = false),
                IndexColumn("w", ColumnType.INT, descending = true)
            ),
        )
        db.createTable("dup", "pk_dup", schema)
        for ((name, keyColumns) in indexes) {
            db.createIndex(name, "pk_dup", "dup", isPrimary = false, isUnique = false, IndexKeySchema(keyColumns))
        }
        val table = db.loadTable("dup").also { t -> rows.forEach(t::insertRow) }

        /** Where [x] sorts relative to [y] in [column]: NULL first ascending, last descending. */
        fun compareColumn(x: Any?, y: Any?, column: IndexColumn): Int {
            val ascending = when {
                x == y -> 0
                x == null -> -1
                y == null -> 1
                else -> (x as Int).compareTo(y as Int)
            }
            return if (column.descending) -ascending else ascending
        }

        fun compareToBound(row: Row, values: List<Any?>, keyColumns: List<IndexColumn>): Int {
            for ((i, value) in values.withIndex()) {
                val c = compareColumn(row[keyColumns[i].name], value, keyColumns[i])
                if (c != 0) return c
            }
            return 0
        }

        val values: List<Any?> = listOf(null, 0, 3, 6, 9)
        val bounds: List<Bound<List<Any?>>> = listOf(unbounded) + (values.map { listOf(it) } + values.flatMap { x ->
            values.map { y ->
                listOf(
                    x,
                    y
                )
            }
        }).flatMap { listOf(Bound(it, true), Bound(it, false)) }

        for ((name, keyColumns) in indexes) {
            `when`("scanning $name forward and backward with every combination of bounds") {
                then("each scan returns exactly the rows in range, in index order") {
                    val usable = bounds.filter { (it.value?.size ?: 0) <= keyColumns.size }
                    val failures = mutableListOf<String>()
                    for (lower in usable) for (upper in usable) {
                        val inRange = rows.filter { row ->
                            val lowerOk = lower.value?.let { v ->
                                val c = compareToBound(row, v, keyColumns); if (lower.isInclusive) c >= 0 else c > 0
                            } ?: true
                            val upperOk = upper.value?.let { v ->
                                val c = compareToBound(row, v, keyColumns); if (upper.isInclusive) c <= 0 else c < 0
                            } ?: true
                            lowerOk && upperOk
                        }.sortedWith { x, y -> compareToBound(x, keyColumns.map { y[it.name] }, keyColumns) }

                        // Backward needs an ORDER BY on the first free key column, against its declared direction.
                        val pinned = (lower.value ?: emptyList()).zip(upper.value ?: emptyList())
                            .takeWhile { (l, u) -> l == u }.size
                        val free = keyColumns.drop(pinned).firstOrNull()
                        val directions = listOfNotNull(
                            false to emptyList<ColumnOrder>(),
                            free?.let { true to listOf(ColumnOrder(it.name, !it.descending)) },
                        )
                        for ((backward, orderBy) in directions) {
                            val want = if (backward) inRange.reversed() else inRange
                            val got = table.selectByRange(name, lower, upper, orderBy)

                            // Rows with the same key may come in any order, so compare the key sequence and the id set.
                            fun keys(rows: List<Row>) = rows.map { row -> keyColumns.map { row[it.name] } }
                            if (keys(got) != keys(want) || ids(got).sorted() != ids(want).sorted()) {
                                failures += "lower=$lower upper=$upper ${if (backward) "BACKWARD" else "FORWARD"}: " +
                                    "expected ${want.size} rows but got ${got.size}"
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
