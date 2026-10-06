package database

import config.SimpleConfig
import config.StorageConfig
import exception.CatalogException
import exception.DatabaseException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import schema.ColumnType
import schema.Row
import schema.RowColumn
import schema.RowSchema
import util.ErrorCode

/**
 * Issue #42: DataBase-wide ReentrantReadWriteLock (DDL = write, DML = read).
 *
 * Deliberately out of scope (a known, documented limitation, not tested here): a [Table] handle
 * obtained *before* a concurrent [DataBase.dropTable] completes, then used *after* - the lock only
 * guarantees no torn/partial reads while a writer is active, not that a caller's already-resolved
 * [schema.IndexHandle]/[index.btree.BTree] references stay valid once the table they came from is
 * gone. A caller that always re-resolves via a fresh [DataBase.loadTable] is fully protected; one
 * that keeps reusing an old [Table] object across a drop is not - see the class's own doc.
 */
class DataBaseConcurrencyTest :
    BehaviorSpec({
        timeout = 5 * 60 * 1000L // 5 minutes - deadlock / hang guard

        given("N threads each creating, filling, and reading back their own table") {
            val dbPath = "test-database-concurrency-own-tables-${Uuid.random()}.db"
            val config = SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 200))
            val db = DataBase(config).apply { initialize() }
            afterSpec {
                db.close()
                File(dbPath).delete()
            }

            val threadCount = 8
            val rowsPerThread = 50L
            val columns =
                RowSchema(
                    listOf(
                        RowColumn("id", ColumnType.LONG, false, 0),
                        RowColumn("value", ColumnType.INT, true, null),
                    )
                )
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())

            `when`("running $threadCount threads concurrently, each on its own table") {
                val threads =
                    (0 until threadCount).map { threadIdx ->
                        Thread {
                            try {
                                val tableName = "concurrent-table-$threadIdx"
                                val table =
                                    db.createTable(
                                        tableName,
                                        "concurrent-primary-$threadIdx",
                                        columns,
                                    )
                                for (id in 1L..rowsPerThread) {
                                    table.insertRow(
                                        Row(columns, listOf(id, (id * threadIdx).toInt()))
                                    )
                                }
                                val reloaded = db.loadTable(tableName)
                                for (id in 1L..rowsPerThread) {
                                    val row =
                                        reloaded.selectByKey(listOf(id))
                                            ?: error(
                                                "thread $threadIdx: missing row $id after reload"
                                            )
                                    val expected = (id * threadIdx).toInt()
                                    if (row["value"] != expected) {
                                        error(
                                            "thread $threadIdx: row $id expected $expected, got ${row["value"]}"
                                        )
                                    }
                                }
                            } catch (e: Throwable) {
                                errors.add(e)
                            }
                        }
                    }
                threads.forEach { it.start() }
                threads.forEach { it.join(60_000) }

                then("no thread should have thrown or produced wrong data") {
                    errors shouldBe emptyList()
                }
                then("every thread should have finished within the timeout") {
                    threads.none { it.isAlive } shouldBe true
                }
            }
        }

        given("N threads racing to create a table with the same name") {
            val dbPath = "test-database-concurrency-name-race-${Uuid.random()}.db"
            val config = SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 200))
            val db = DataBase(config).apply { initialize() }
            afterSpec {
                db.close()
                File(dbPath).delete()
            }

            val threadCount = 8
            val tableName = "race-table"
            val columns = RowSchema(listOf(RowColumn("id", ColumnType.LONG, false, 0)))
            val readyLatch = CountDownLatch(threadCount)
            val startLatch = CountDownLatch(1)
            val successes = AtomicInteger(0)
            val duplicateFailures = AtomicInteger(0)
            val unexpected = Collections.synchronizedList(mutableListOf<Throwable>())

            `when`(
                "$threadCount threads call createTable with the same name at (as close to) the same time"
            ) {
                val threads =
                    (0 until threadCount).map {
                        Thread {
                            readyLatch.countDown()
                            startLatch.await()
                            try {
                                db.createTable(tableName, "race-primary", columns)
                                successes.incrementAndGet()
                            } catch (e: Throwable) {
                                if (e is DatabaseException && e.code == ErrorCode.DUPLICATE_TABLE) {
                                    duplicateFailures.incrementAndGet()
                                } else {
                                    unexpected.add(e)
                                }
                            }
                        }
                    }
                threads.forEach { it.start() }
                readyLatch.await()
                startLatch.countDown()
                threads.forEach { it.join(60_000) }

                then("exactly one thread should succeed") {
                    successes.get() shouldBe 1
                }
                then("every other thread should get DuplicateTable, and nothing else") {
                    duplicateFailures.get() shouldBe (threadCount - 1)
                    unexpected shouldBe emptyList()
                }
            }
        }

        given(
            "a table being dropped while another thread repeatedly does a fresh loadTable + select"
        ) {
            val dbPath = "test-database-concurrency-drop-vs-select-${Uuid.random()}.db"
            val config = SimpleConfig(StorageConfig(dbPath = dbPath, poolSize = 200))
            val db = DataBase(config).apply { initialize() }
            afterSpec {
                db.close()
                File(dbPath).delete()
            }

            val tableName = "drop-race-table"
            val primaryIdxName = "drop-race-primary"
            val columns = RowSchema(listOf(RowColumn("id", ColumnType.LONG, false, 0)))
            val table = db.createTable(tableName, primaryIdxName, columns)
            val rowCount = 300L
            for (id in 1L..rowCount) {
                table.insertRow(Row(columns, listOf(id)))
            }

            `when`("dropping the table while a reader keeps re-resolving it fresh each attempt") {
                val stop = AtomicBoolean(false)
                val unexpected = Collections.synchronizedList(mutableListOf<Throwable>())

                val reader = Thread {
                    while (!stop.get()) {
                        try {
                            val reloaded = db.loadTable(tableName)
                            reloaded.selectByKey(listOf(1L))
                        } catch (e: Throwable) {
                            // Expected once the drop has become visible - the table is gone.
                            val tableGone =
                                (e is CatalogException && e.code == ErrorCode.UNDEFINED_TABLE) ||
                                    (e is DatabaseException && e.code == ErrorCode.UNDEFINED_OBJECT)
                            if (!tableGone) unexpected.add(e)
                            stop.set(true)
                        }
                    }
                }
                reader.start()
                Thread.sleep(
                    5
                ) // give the reader a head start so the drop lands mid-flight, not before it even
                  // begins
                db.dropTable(tableName)
                stop.set(true)
                reader.join(60_000)

                then(
                    "the reader never sees anything but a clean success or UndefinedTable/UndefinedObject"
                ) {
                    unexpected shouldBe emptyList()
                }
                then("the reader thread finished within the timeout") {
                    reader.isAlive shouldBe false
                }
            }
        }
    })
