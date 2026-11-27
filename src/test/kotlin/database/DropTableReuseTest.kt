package database

import config.SimpleConfig
import config.StorageConfig
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import schema.Bound
import schema.ColumnType
import schema.Row
import schema.RowColumn
import schema.RowSchema
import java.io.File
import kotlin.uuid.Uuid

/**
 * #82 on a real database: dropping a table frees its pages, and the next table reuses them. With a pool small enough
 * to evict, inserting into the new table used to throw `PAGE_NOT_FOUND_IN_CACHE` (see `BufferPoolPageReuseTest`).
 */
class DropTableReuseTest : BehaviorSpec({
    val schema = RowSchema(listOf(RowColumn("id", ColumnType.LONG, false, 0), RowColumn("v", ColumnType.INT)))
    val everything = Bound<List<Any?>>(null, true)

    // (rows, poolSize): the combinations that failed in #82; a big pool, which never evicts, as a control.
    for ((n, poolSize) in listOf(1000 to 30, 5000 to 100, 5000 to 10000)) {
        given("a table of $n rows dropped from a database with a pool of $poolSize frames") {
            `when`("new tables are created and filled on the freed pages") {
                then("every insert succeeds and every row reads back") {
                    val dbPath = "test-drop-table-reuse-${Uuid.random()}.db"
                    val db = DataBase(SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = poolSize))).apply { initialize() }
                    try {
                        db.createTable("a", "pk_a", schema)
                        db.loadTable("a").also { t -> for (id in 1L..n) t.insertRow(Row(schema, listOf<Any?>(id, 1))) }
                        db.dropTable("a")

                        db.createTable("b", "pk_b", schema)
                        val b = db.loadTable("b").also { t -> for (id in 1L..n) t.insertRow(Row(schema, listOf<Any?>(id, 2))) }
                        withClue("table b") {
                            b.selectByRange("pk_b", everything, everything, emptyList()).map { it["id"] } shouldBe (1L..n).toList()
                        }

                        db.createTable("c", "pk_c", schema)
                        val c = db.loadTable("c").also { t -> t.insertRow(Row(schema, listOf<Any?>(1L, 3))) }
                        withClue("table c") {
                            c.selectByRange("pk_c", everything, everything, emptyList()).size shouldBe 1
                        }
                    } finally {
                        runCatching { db.close() }
                        File(dbPath).delete()
                    }
                }
            }
        }
    }
})
