package sql.planner

import config.SimpleConfig
import config.StorageConfig
import database.DataBase
import database.Table
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import schema.Bound
import schema.ColumnOrder
import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import schema.Row
import schema.RowColumn
import schema.RowSchema
import sql.SqlParser
import sql.ast.Select
import sql.expr.bind
import java.io.File
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * Runs the planner's plans against a real table and checks they return what a plain full scan + filter + sort
 * returns. The unit tests check the plan's *shape*; this checks the shape keeps `Table.selectByRange`'s contracts
 * (byte-order bounds, NULL placement, pinned columns, scan direction) so the rows come out right.
 */
class PlanExecutionTest : BehaviorSpec({
    val dbPath = "test-plan-execution-${Uuid.random()}.db"
    val db = DataBase(SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 100))).apply { initialize() }
    afterSpec { db.close(); File(dbPath).delete() }

    val schema = RowSchema(
        listOf(
            RowColumn("id", ColumnType.LONG, false, 0),
            RowColumn("a", ColumnType.INT),
            RowColumn("b", ColumnType.INT),
            RowColumn("name", ColumnType.STRING),
        )
    )

    /** 80 rows with repeats and NULLs in every non-key column, so ranges and sorts have ties and NULLs to get wrong. */
    val duplicateRows: List<Row> = (1L..80L).map { id ->
        Row(
            schema,
            listOf(
                id,
                if (id % 9 == 0L) null else (id % 5).toInt(),
                if (id % 7 == 0L) null else ((id * 3) % 11).toInt(),
                if (id % 6 == 0L) null else listOf("kim", "lee", "park", "choi")[(id % 4).toInt()],
            )
        )
    }

    /**
     * 120 rows where no index key repeats: `b` is distinct (or NULL for id 1..6, each with a different `a`), and `a` is
     * NULL only where `b` is distinct. NULLs are still there for ranges to leak. The counterpart of [duplicateRows]:
     * scans here never have to step over equal keys.
     */
    val distinctKeyRows: List<Row> = (1L..120L).map { id ->
        Row(
            schema,
            listOf(
                id,
                if (id > 114) null else (id % 6).toInt(),
                if (id <= 6) null else ((id * 37) % 121).toInt(),
                listOf("kim", "lee", "park", "choi", null)[(id % 5).toInt()],
            )
        )
    }

    /** A table and the rows put in it, which the brute-force answer is computed from. */
    class Fixture(val table: Table, val rows: List<Row>)

    /** Creates table [name] with [indexes] as (index name, key columns), fills it with [rows], and loads it back. */
    fun table(name: String, rows: List<Row>, vararg indexes: Pair<String, List<IndexColumn>>): Fixture {
        db.createTable(name, "pk_$name", schema)
        for ((indexName, keyColumns) in indexes) {
            db.createIndex(indexName, "pk_$name", name, isPrimary = false, isUnique = false, IndexKeySchema(keyColumns))
        }
        val table = db.loadTable(name).also { table -> rows.forEach(table::insertRow) }
        return Fixture(table, rows)
    }

    fun asc(name: String) = IndexColumn(name, ColumnType.INT, descending = false)
    fun desc(name: String) = IndexColumn(name, ColumnType.INT, descending = true)

    /** NULL first in an ascending column, last in a descending one - the same place the index puts it. */
    fun comparator(orderBy: List<ColumnOrder>): Comparator<Row> =
        orderBy.map { order ->
            @Suppress("UNCHECKED_CAST")
            val ascending = Comparator<Row> { x, y -> compareValues(x[order.name] as Comparable<Any>?, y[order.name] as Comparable<Any>?) }
            if (order.descending) ascending.reversed() else ascending
        }.reduceOrNull { first, next -> first.then(next) } ?: Comparator { _, _ -> 0 }

    /** A minimal executor: just enough to run a [Plan] the way the real one (#56) will. */
    fun execute(table: Table, plan: Plan): List<Row> = when (plan) {
        is IndexScan -> table.selectByRange(plan.indexName, plan.lower, plan.upper, plan.orderBy)
        is FullScan -> {
            val all = Bound<List<Any?>>(null, true)
            table.selectByRange(table.indexes.first { it.isPrimary }.indexName, all, all, plan.orderBy)
        }
        is Filter -> execute(table, plan.child).filter { plan.predicate.test(it).isTrue }
        is Sort -> execute(table, plan.child).sortedWith(comparator(plan.orderBy))
        is Limit -> execute(table, plan.child).drop(plan.offset ?: 0).let { rows -> plan.limit?.let(rows::take) ?: rows }
    }

    /**
     * Plans and runs [sql] on [table], and checks it against the brute-force answer:
     * - the same rows (by id), when there is no LIMIT/OFFSET;
     * - with an ORDER BY, the same sequence of ORDER BY values (rows that tie may come in any order, and LIMIT may
     *   pick any of the tied ones);
     * - with a LIMIT/OFFSET but no ORDER BY, the same count, all of them correct rows.
     */
    fun check(fixture: Fixture, sql: String) = withClue(sql) {
        val table = fixture.table
        val select = SqlParser.parse(sql) as Select
        val where = select.where?.let { bind(it, table.rowSchema) }
        val plan = Planner(table.rowSchema, table.indexes).plan(select, where)
        val orderBy = select.orderBy.map { ColumnOrder(it.column, it.descending) }

        val matching = fixture.rows.filter { where == null || where.test(it).isTrue }.sortedWith(comparator(orderBy))
        val expected = matching
            .drop(select.offset?.toInt() ?: 0)
            .let { rows -> select.limit?.let { rows.take(it.toInt()) } ?: rows }

        val actual = withClue("plan: $plan") { execute(table, plan) }

        withClue("plan: $plan") {
            actual.size shouldBe expected.size
            if (orderBy.isNotEmpty()) {
                actual.map { row -> orderBy.map { row[it.name] } } shouldBe expected.map { row -> orderBy.map { row[it.name] } }
            }
            if (select.limit == null && select.offset == null) {
                actual.map { it["id"] as Long }.sorted() shouldBe expected.map { it["id"] as Long }.sorted()
            } else {
                matching.map { it["id"] } shouldContainAll actual.map { it["id"] }
            }
        }
    }

    val queries = listOf(
        // no WHERE
        "select * from t",
        // equality prefix, then a range on the next column
        "select * from t where a = 1",
        "select * from t where a = 1 and b = 3",
        "select * from t where a = 2 order by name", // used to seek onto the (a=2, b=NULL) key when prefixes were padded
        "select * from t where a = 1 and b > 3",
        "select * from t where a = 1 and b <= 3",
        "select * from t where a = 1 and b >= 3 and b < 8",
        "select * from t where a = 1 and b > 3 and b > 5",
        "select * from t where 1 = a and 3 < b",
        // a range on the first column: NULLs sit at one end and must not leak in
        "select * from t where a < 2",
        "select * from t where a > 2",
        "select * from t where a >= 1 and a <= 3",
        // the DESC index: bounds swap sides
        "select * from t where b > 5",
        "select * from t where b < 4",
        "select * from t where 5 < b",
        "select * from t where b >= 5 and b <= 5",
        "select * from t where b > 5 and b < 5",
        // the primary key
        "select * from t where id = 42",
        "select * from t where id > 70",
        "select * from t where id <= 5 and a = 1",
        // literals that can't be index keys as written
        "select * from t where a = 1.0",
        "select * from t where a = 1.5",
        "select * from t where a > 1.5",
        "select * from t where a = 3000000000",
        // what no index can use
        "select * from t where a = 1 or b = 2",
        "select * from t where a <> 1",
        "select * from t where not (a = 1)",
        "select * from t where a is null",
        "select * from t where a is not null and b > 3",
        "select * from t where name = 'kim'",
        "select * from t where name = 'kim' and a = 2",
        // ORDER BY an index gives, forward or backward
        "select * from t order by a",
        "select * from t order by a desc",
        "select * from t order by a, b",
        "select * from t order by a desc, b desc",
        "select * from t where a = 1 order by b",
        "select * from t where a = 1 order by b desc",
        "select * from t where a = 1 order by a, b",
        "select * from t where a = 1 order by a desc, b desc",
        "select * from t order by b desc",
        "select * from t order by b",
        "select * from t where b > 3 order by b desc",
        "select * from t order by id desc",
        // a range scanned backward: the seek starts from the upper bound
        "select * from t where a = 1 and b > 3 order by b desc",
        "select * from t where a = 1 and b < 8 order by b desc",
        "select * from t where a = 1 and b > 2 and b <= 8 order by a desc, b desc",
        "select * from t where a > 1 order by a desc",
        "select * from t where a < 3 order by a desc, b desc",
        "select * from t where b > 3 order by b",
        "select * from t where b < 7 order by b",
        "select * from t where id > 70 order by id desc",
        "select * from t where id >= 10 and id < 20 order by id desc",
        "select * from t where id > 70 order by id desc limit 3 offset 2",
        // ORDER BY that needs a Sort
        "select * from t order by a, b desc",
        "select * from t order by name",
        "select * from t order by name desc, id",
        "select * from t where a > 1 order by b",
        // LIMIT / OFFSET
        "select * from t order by id limit 5",
        "select * from t order by id desc limit 5 offset 3",
        "select * from t where a = 1 order by b limit 3",
        "select * from t order by name limit 4 offset 2",
        "select * from t order by b desc limit 10",
        "select * from t order by a limit 3 offset 100",
        "select * from t where a = 1 limit 2",
        "select * from t limit 0",
        "select * from t offset 75",
    )

    given("a table with an (a, b) index and a b DESC index") {
        val t = table("t", duplicateRows, "idx_ab" to listOf(asc("a"), asc("b")), "idx_b_desc" to listOf(desc("b")))

        `when`("each query is planned and run") {
            then("it returns what a full scan + filter + sort returns") {
                for (sql in queries) check(t, sql)
            }
        }

        // Seeks that land exactly on a key stored many times: they used to start partway through the duplicates (#77).
        `when`("the scan has to seek onto duplicate keys") {
            then("it returns what a full scan + filter + sort returns") {
                for (sql in listOf(
                    "select * from t where b = 5",
                    "select * from t where b >= 2 and b <= 6",
                    "select * from t where b >= 0",
                    "select * from t where a = 1 and b = 3",
                    "select * from t where a = 1 and b >= 3 and b < 8",
                )) check(t, sql)
            }
        }

        // Random queries on this duplicate-heavy table too.
        `when`("hundreds of random queries are planned and run") {
            then("every one returns what a full scan + filter + sort returns") {
                val randomQueries = Random(77).let { random -> List(400) { randomQuery(random, table = "t", bValues = -2..12) } }
                for (sql in randomQueries) check(t, sql)
            }
        }
    }

    given("the same indexes on a table whose index keys never repeat") {
        val u = table("u", distinctKeyRows, "idx_u_ab" to listOf(asc("a"), asc("b")), "idx_u_b_desc" to listOf(desc("b")))

        // Fixed seed, so a failure always reproduces; the SQL and the plan are in the failure message.
        val randomQueries = Random(55).let { random -> List(400) { randomQuery(random) } }

        // Includes backward index scans: before #78 was fixed, `order by b` (a backward walk of the b DESC index)
        // returned 88 of these 120 rows, because a leaf split left the old right neighbour's left link stale.
        `when`("hundreds of random queries are planned and run") {
            then("every one returns what a full scan + filter + sort returns") {
                for (sql in randomQueries) check(u, sql)
            }
        }
    }

    // `a = 1` on (a, b DESC) pins only the prefix, so the scan starts from a [1] prefix bound - the shape #76 broke.
    given("a table whose only secondary index is (a, b DESC), with keys that never repeat") {
        val w = table("w", distinctKeyRows, "idx_w_a_bdesc" to listOf(asc("a"), desc("b")))
        val randomQueries = Random(76).let { random -> List(400) { randomQuery(random) } }

        `when`("prefix queries on it are planned and run") {
            then("they return what a full scan + filter + sort returns") {
                for (sql in listOf(
                    "select * from w where a = 1",
                    "select * from w where a = 1 and b > 40",
                    "select * from w where a = 1 and b <= 40",
                    "select * from w where a = 1 order by b desc",
                    "select * from w where a >= 2",
                    "select * from w where a < 3",
                    "select * from w where a > 1 and a <= 4 order by a",
                )) check(w, sql)
            }
        }

        `when`("hundreds of random queries are planned and run") {
            then("every one returns what a full scan + filter + sort returns") {
                for (sql in randomQueries) check(w, sql)
            }
        }
    }
})

/**
 * A random `select * from [table] ...`: one to three AND-ed terms (mostly index-friendly comparisons, some that only a
 * Filter can handle), an optional ORDER BY of up to two columns in any direction, and sometimes LIMIT / OFFSET.
 */
private fun randomQuery(random: Random, table: String = "u", bValues: IntRange = -5..125): String {
    fun literal(column: String): String = when (column) {
        "a" -> if (random.nextInt(10) == 0) listOf("2.0", "2.5", "-1")[random.nextInt(3)] else random.nextInt(-1, 7).toString()
        "name" -> listOf("'kim'", "'lee'", "'park'", "'choi'", "'a'", "'zz'")[random.nextInt(6)]
        else -> random.nextInt(bValues.first, bValues.last + 1).toString() // b, id
    }

    fun comparison(): String {
        val column = listOf("a", "a", "b", "b", "id", "name")[random.nextInt(6)]
        val op = listOf("=", "=", "<>", "<", "<=", ">", ">=")[random.nextInt(7)]
        val value = literal(column)
        return if (random.nextInt(4) == 0) "$value $op $column" else "$column $op $value"
    }

    fun term(): String = when (random.nextInt(12)) {
        0 -> "${listOf("a", "b", "name")[random.nextInt(3)]} is ${if (random.nextBoolean()) "" else "not "}null"
        1 -> "(${comparison()} or ${comparison()})"
        2 -> "not (${comparison()})"
        3 -> "${listOf("a", "b")[random.nextInt(2)]} = null"
        else -> comparison()
    }

    val sql = StringBuilder("select * from $table")
    val terms = random.nextInt(4)
    if (terms > 0) sql.append(" where ").append(List(terms) { term() }.joinToString(" and "))

    val orderColumns = listOf("a", "b", "id", "name").shuffled(random).take(random.nextInt(3))
    if (orderColumns.isNotEmpty()) {
        sql.append(" order by ").append(orderColumns.joinToString(", ") { if (random.nextBoolean()) "$it desc" else it })
    }

    if (random.nextInt(4) == 0) sql.append(" limit ").append(random.nextInt(0, 11))
    if (random.nextInt(6) == 0) sql.append(" offset ").append(random.nextInt(0, 21))
    return sql.toString()
}
