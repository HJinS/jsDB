package database

import exception.DatabaseException
import exception.TableException
import index.btree.BTree
import index.btree.Cursor
import index.btree.ScanDirection
import index.serializer.BinaryRowSerializer
import index.serializer.MultiColumnKeySerializer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.clearMocks
import java.util.concurrent.locks.ReentrantReadWriteLock
import schema.Bound
import schema.ColumnOrder
import schema.ColumnType
import schema.IndexColumn
import schema.IndexHandle
import schema.IndexKeySchema
import schema.IndexRow
import schema.Row
import schema.RowColumn
import schema.RowSchema
import util.SqlState

class TableTest :
    BehaviorSpec({
        val rowSchema =
            RowSchema(
                listOf(
                    RowColumn("id", ColumnType.LONG, false, 0),
                    RowColumn("email", ColumnType.STRING, true, null),
                )
            )

        val primaryKeySerializer =
            MultiColumnKeySerializer(
                IndexKeySchema(listOf(IndexColumn("id", ColumnType.LONG, false)))
            )
        val primaryValueSerializer = BinaryRowSerializer(rowSchema)
        val secondaryKeySerializer =
            MultiColumnKeySerializer(
                IndexKeySchema(listOf(IndexColumn("email", ColumnType.STRING, false)))
            )
        val secondaryValueSerializer =
            BinaryRowSerializer(RowSchema(listOf(RowColumn("id", ColumnType.LONG, false, 0))))

        // mockk matches ByteArray args by reference by default, so every serialized key/value
        // needs a content-based matcher instead of a plain value.
        fun MockKMatcherScope.eqBytes(expected: ByteArray) =
            match<ByteArray> { it.contentEquals(expected) }

        fun primaryHandle(btree: BTree) =
            IndexHandle(
                IndexRow(
                    indexId = 0L,
                    indexName = "pk_idx",
                    tableName = "users",
                    rootPageId = null,
                    isPrimary = true,
                    isUnique = true,
                    keyColumns = listOf(IndexColumn("id", ColumnType.LONG, false)),
                ),
                btree,
                primaryKeySerializer,
                primaryValueSerializer,
            )

        fun secondaryHandle(btree: BTree, isUnique: Boolean = true) =
            IndexHandle(
                IndexRow(
                    indexId = 1L,
                    indexName = "email_idx",
                    tableName = "users",
                    rootPageId = null,
                    isPrimary = false,
                    isUnique = isUnique,
                    keyColumns = listOf(IndexColumn("email", ColumnType.STRING, false)),
                ),
                btree,
                secondaryKeySerializer,
                secondaryValueSerializer,
            )

        // Two free columns (col1 ASC, col2 DESC) so scan-direction resolution actually has
        // something non-trivial to check — the single-column indexes above can't exercise
        // the "declared direction vs. requested direction" comparison at all.
        val compositeKeyColumns =
            listOf(
                IndexColumn("col1", ColumnType.LONG, descending = false),
                IndexColumn("col2", ColumnType.LONG, descending = true),
            )
        val compositeKeySerializer = MultiColumnKeySerializer(IndexKeySchema(compositeKeyColumns))
        val compositeValueSerializer =
            BinaryRowSerializer(RowSchema(listOf(RowColumn("id", ColumnType.LONG, false, 0))))

        // Table's DML methods just need *some* Lock to acquire per call - these tests exercise a
        // single Table in isolation (no DataBase, no cross-table sharing to verify), so a
        // throwaway lock of its own is enough; see DataBase for the real shared-lock wiring.
        val testReadLock = ReentrantReadWriteLock().readLock()

        fun compositeHandle(btree: BTree) =
            IndexHandle(
                IndexRow(
                    indexId = 2L,
                    indexName = "composite_idx",
                    tableName = "users",
                    rootPageId = null,
                    isPrimary = false,
                    isUnique = false,
                    keyColumns = compositeKeyColumns,
                ),
                btree,
                compositeKeySerializer,
                compositeValueSerializer,
            )

        given("a table with an empty primary index and a unique secondary index") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns null
            every {
                secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))))
            } returns null
            every { primaryBtree.insert(any(), any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("inserting a new row") {
                table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))

                then("the primary index should store the full row") {
                    verify {
                        primaryBtree.insert(
                            eqBytes(primaryKeySerializer.serialize(listOf(1L))),
                            eqBytes(primaryValueSerializer.serialize(listOf(1L, "a@x.com"))),
                        )
                    }
                }
                then("the secondary index should store the primary key") {
                    verify {
                        secondaryBtree.insert(
                            eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))),
                            eqBytes(secondaryValueSerializer.serialize(listOf(1L))),
                        )
                    }
                }
            }
        }

        given("a table whose primary index already has that key") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "old@x.com"))

            `when`("inserting a row with that primary key") {
                then("UniqueViolation should be thrown") {
                    shouldThrow<TableException.UniqueViolation> {
                        table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))
                    }
                }
            }
        }

        given("a table whose unique secondary index already has that key") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns null
            every {
                secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))))
            } returns secondaryValueSerializer.serialize(listOf(99L))

            `when`("inserting a row with that secondary key") {
                then("UniqueViolation should be thrown") {
                    shouldThrow<TableException.UniqueViolation> {
                        table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))
                    }
                }
            }
        }

        given("a table with a non-unique secondary index that already has that key") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree, isUnique = false)),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns null
            every { primaryBtree.insert(any(), any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("inserting a row with that secondary key") {
                table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))

                then("no existence check should happen and the row should just be inserted") {
                    verify(exactly = 0) { secondaryBtree.search(any()) }
                    verify {
                        secondaryBtree.insert(
                            eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))),
                            eqBytes(secondaryValueSerializer.serialize(listOf(1L))),
                        )
                    }
                }
            }
        }

        given("a table with a row stored at a key") {
            val primaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "a@x.com"))

            `when`("selecting by that key") {
                then("the row should be returned") {
                    table.selectByKey(listOf(1L)).shouldNotBeNull {
                        this["email"] shouldBe "a@x.com"
                    }
                }
            }
        }

        given("a table with no row at a key") {
            val primaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(999L))))
            } returns null

            `when`("selecting by that key") {
                then("null should be returned") {
                    table.selectByKey(listOf(999L)) shouldBe null
                }
            }
        }

        given("a table with a secondary index whose key resolves to a stored primary key") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))))
            } returns secondaryValueSerializer.serialize(listOf(1L))
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "a@x.com"))

            `when`("selecting via that secondary index") {
                then("the row should be found via the two-step lookup") {
                    table.selectByIndex("email_idx", listOf("a@x.com")).shouldNotBeNull {
                        this["id"] shouldBe 1L
                    }
                }
            }
        }

        given("a table with a secondary index whose key does not resolve to anything") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                secondaryBtree.search(
                    eqBytes(secondaryKeySerializer.serialize(listOf("nope@x.com")))
                )
            } returns null

            `when`("selecting via that secondary index") {
                then("null should be returned") {
                    table.selectByIndex("email_idx", listOf("nope@x.com")) shouldBe null
                }
            }
        }

        given("a table without a secondary index of a given name") {
            val primaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )

            `when`("selecting via that unknown index name") {
                then("UndefinedIndex should be thrown") {
                    shouldThrow<TableException.UndefinedIndex> {
                        table.selectByIndex("no-such-index", listOf("x"))
                    }
                }
            }
        }

        given("a table with no row at the primary key being updated") {
            val primaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns null

            `when`("updating that row") {
                then("RowNotFound should be thrown") {
                    shouldThrow<TableException.RowNotFound> {
                        table.updateRow(listOf(1L), Row(rowSchema, listOf(1L, "new@x.com")))
                    }
                }
            }
        }

        given("a table where the new row's primary key differs from the key being updated") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )

            `when`("updating the row at id 1 with a new row whose id is 2") {
                then("PrimaryKeyUpdateNotSupported is thrown with the FEATURE_NOT_SUPPORTED state") {
                    val error = shouldThrow<TableException.PrimaryKeyUpdateNotSupported> {
                        table.updateRow(
                            listOf(1L),
                            Row(rowSchema, listOf(2L, "a@x.com"))
                        )
                    }
                    error.message shouldContain SqlState.FEATURE_NOT_SUPPORTED.code
                }
                then("no index is read or written, so the row already stored at id 2 is not overwritten") {
                    verify(exactly = 0) { primaryBtree.search(any()) }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                    verify(exactly = 0) { secondaryBtree.update(any(), any(), any()) }
                    verify(exactly = 0) { secondaryBtree.delete(any()) }
                    verify(exactly = 0) { secondaryBtree.insert(any(), any()) }
                }
            }
        }

        given("a table updated with a new row that has null in a NOT NULL column") {
            val primaryBtree = mockk<BTree>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)

            `when`("updating with a null id") {
                then("NotNullViolation naming that column is thrown before any index is touched") {
                    val error = shouldThrow<DatabaseException.NotNullViolation> {
                        table.updateRow(listOf(1L), Row(rowSchema, listOf(null, "a@x.com")))
                    }
                    error.message shouldContain "'id'"
                    error.message shouldContain SqlState.NOT_NULL_VIOLATION.code
                    verify(exactly = 0) { primaryBtree.search(any()) }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                }
            }
        }

        given("a table with a row whose secondary key stays the same after update") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "a@x.com"))
            every { primaryBtree.update(any(), any(), any()) } just Runs
            every { secondaryBtree.update(any(), any(), any()) } just Runs

            `when`("updating that row") {
                table.updateRow(listOf(1L), Row(rowSchema, listOf(1L, "a@x.com")))

                then("the secondary index should be updated in place, not deleted and reinserted") {
                    verify {
                        secondaryBtree.update(
                            eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))),
                            eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))),
                            eqBytes(secondaryValueSerializer.serialize(listOf(1L))),
                        )
                    }
                    verify(exactly = 0) { secondaryBtree.delete(any()) }
                    verify(exactly = 0) { secondaryBtree.insert(any(), any()) }
                }
            }
        }

        given("a table with a row whose secondary key changes after update") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "old@x.com"))
            every { primaryBtree.update(any(), any(), any()) } just Runs
            // email_idx is unique, so updateRow checks that the new email is not already taken.
            every { secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("new@x.com")))) } returns null
            every { secondaryBtree.delete(any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("updating that row") {
                table.updateRow(listOf(1L), Row(rowSchema, listOf(1L, "new@x.com")))

                then("the old secondary entry should be deleted and a new one inserted") {
                    verify {
                        secondaryBtree.delete(
                            eqBytes(secondaryKeySerializer.serialize(listOf("old@x.com")))
                        )
                    }
                    verify {
                        secondaryBtree.insert(
                            eqBytes(secondaryKeySerializer.serialize(listOf("new@x.com"))),
                            eqBytes(secondaryValueSerializer.serialize(listOf(1L))),
                        )
                    }
                }
            }
        }

        given("a table with no row at the primary key being deleted") {
            val primaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns null

            `when`("deleting that row") {
                then("RowNotFound should be thrown") {
                    shouldThrow<TableException.RowNotFound> { table.deleteRow(listOf(1L)) }
                }
            }
        }

        given("a table with a row to delete") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "a@x.com"))
            every { secondaryBtree.delete(any()) } just Runs
            every { primaryBtree.delete(any()) } just Runs

            `when`("deleting that row") {
                table.deleteRow(listOf(1L))

                then("the row should be removed from the secondary index using its stored key") {
                    verify {
                        secondaryBtree.delete(
                            eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com")))
                        )
                    }
                }
                then("the row should be removed from the primary index") {
                    verify {
                        primaryBtree.delete(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
                    }
                }
            }
        }

        given("an inclusive range scan whose cursor walks one entry past the upper bound") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )

            // id = 4 sits just past the serializeUpper(3) stop boundary and must never make it
            // into the result — this is exactly the off-by-one that once let a boundary-crossing
            // row slip in because the stop check ran after result.add() instead of before it.
            val entries =
                listOf(1L, 2L, 3L, 4L).map {
                    primaryKeySerializer.serialize(listOf(it)) to
                            primaryValueSerializer.serialize(listOf(it, "$it@x.com"))
                }
            every { cursor.step() } returnsMany entries
            every { cursor.close() } just Runs
            every {
                primaryBtree.search(
                    eqBytes(primaryKeySerializer.serialize(listOf(1L))),
                    ScanDirection.FORWARD,
                    true,
                )
            } returns cursor

            `when`("scanning id in [1, 3] inclusive") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(listOf(1L), isInclusive = true),
                        Bound(listOf(3L), isInclusive = true),
                        emptyList(),
                    )

                then("only ids 1 through 3 are returned, in order") {
                    result.map { it["id"] } shouldBe listOf(1L, 2L, 3L)
                }
                then("the cursor is closed once the stop condition breaks the loop") {
                    verify { cursor.close() }
                }
            }
        }

        given("a range scan with no lower bound") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )

            val entry =
                primaryKeySerializer.serialize(listOf(2L)) to
                        primaryValueSerializer.serialize(listOf(2L, "b@x.com"))
            // A row past the upper bound sits right after it in the mocked sequence, so the test
            // can tell "the stop condition actually excluded id = 6" apart from "the cursor just
            // happened to run out of scripted entries on its own".
            val outOfBoundEntry =
                primaryKeySerializer.serialize(listOf(6L)) to
                        primaryValueSerializer.serialize(listOf(6L, "f@x.com"))
            every { cursor.step() } returnsMany listOf(entry, outOfBoundEntry, null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor

            `when`("scanning with lowerBound.value == null") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(listOf(5L), isInclusive = true),
                        emptyList(),
                    )

                then("the seek is issued with boundGiven = false and no key") {
                    verify { primaryBtree.search(null, ScanDirection.FORWARD, false) }
                }
                then("rows up to the upper bound are returned, and the row past it is excluded") {
                    result.map { it["id"] } shouldBe listOf(2L)
                }
            }
        }

        given("a range scan with no upper bound") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )

            val entries =
                listOf(1L, 2L).map {
                    primaryKeySerializer.serialize(listOf(it)) to
                            primaryValueSerializer.serialize(listOf(it, "$it@x.com"))
                }
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs
            every {
                primaryBtree.search(
                    eqBytes(primaryKeySerializer.serialize(listOf(1L))),
                    ScanDirection.FORWARD,
                    true,
                )
            } returns cursor

            `when`("scanning with upperBound.value == null") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(listOf(1L), isInclusive = true),
                        Bound(null, isInclusive = true),
                        emptyList(),
                    )

                then("every row up to the cursor's own natural end is returned") {
                    result.map { it["id"] } shouldBe listOf(1L, 2L)
                }
                then("the cursor is still closed") {
                    verify { cursor.close() }
                }
            }
        }

        given("a range scan on an unknown index name") {
            val table =
                Table(
                    rowSchema,
                    primaryHandle(mockk<BTree>()),
                    emptyMap(),
                    testReadLock,
                )

            `when`("selecting a range on that name") {
                then("UndefinedIndex should be thrown") {
                    shouldThrow<TableException.UndefinedIndex> {
                        table.selectByRange(
                            "no-such-index",
                            Bound(listOf(1L), isInclusive = true),
                            Bound(listOf(5L), isInclusive = true),
                            emptyList(),
                        )
                    }
                }
            }
        }

        given("a prefix search on a secondary index") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )

            val entry =
                secondaryKeySerializer.serialize(listOf("a@x.com")) to
                        secondaryValueSerializer.serialize(listOf(1L))
            every { cursor.step() } returnsMany listOf(entry, null)
            every { cursor.close() } just Runs
            every {
                secondaryBtree.search(
                    eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))),
                    ScanDirection.FORWARD,
                    true,
                )
            } returns cursor
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "a@x.com"))

            `when`("selecting by that prefix") {
                val result = table.selectByPrefix("email_idx", listOf("a@x.com"), emptyList())

                then("both bounds are seeked as the same inclusive value") {
                    verify {
                        secondaryBtree.search(
                            eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))),
                            ScanDirection.FORWARD,
                            true,
                        )
                    }
                }
                then("the row is resolved by re-reading the primary index") {
                    result.map { it["email"] } shouldBe listOf("a@x.com")
                }
            }
        }

        given("a prefix search whose secondary index points at a missing primary key") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )

            val entry =
                secondaryKeySerializer.serialize(listOf("a@x.com")) to
                        secondaryValueSerializer.serialize(listOf(1L))
            every { cursor.step() } returns entry
            every { cursor.close() } just Runs
            every {
                secondaryBtree.search(
                    eqBytes(secondaryKeySerializer.serialize(listOf("a@x.com"))),
                    ScanDirection.FORWARD,
                    true,
                )
            } returns cursor
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns null

            `when`("selecting by that prefix") {
                then("CorruptedIndex is thrown, and the cursor is still closed") {
                    shouldThrow<TableException.CorruptedIndex> {
                        table.selectByPrefix("email_idx", listOf("a@x.com"), emptyList())
                    }
                    verify { cursor.close() }
                }
            }
        }

        given("a composite index with orderBy matching its declared per-column direction") {
            val compositeBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(mockk<BTree>()),
                    mapOf("composite_idx" to compositeHandle(compositeBtree)),
                    testReadLock,
                )
            every { compositeBtree.search(any(), any(), any()) } returns null

            `when`("scanning with orderBy = col1 asc, col2 desc (exactly as declared)") {
                table.selectByRange(
                    "composite_idx",
                    Bound(listOf(1L, 10L), isInclusive = true),
                    Bound(listOf(5L, 20L), isInclusive = true),
                    listOf(
                        ColumnOrder("col1", descending = false),
                        ColumnOrder("col2", descending = true),
                    ),
                )

                then("the scan resolves to FORWARD") {
                    verify { compositeBtree.search(any(), ScanDirection.FORWARD, any()) }
                }
            }
        }

        given("a composite index with orderBy exactly reversed from its declared direction") {
            val compositeBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(mockk<BTree>()),
                    mapOf("composite_idx" to compositeHandle(compositeBtree)),
                    testReadLock,
                )
            every { compositeBtree.search(any(), any(), any()) } returns null

            `when`("scanning with orderBy = col1 desc, col2 asc (exact opposite of declared)") {
                table.selectByRange(
                    "composite_idx",
                    Bound(listOf(1L, 10L), isInclusive = true),
                    Bound(listOf(5L, 20L), isInclusive = true),
                    listOf(
                        ColumnOrder("col1", descending = true),
                        ColumnOrder("col2", descending = false),
                    ),
                )

                then("the scan resolves to BACKWARD") {
                    verify { compositeBtree.search(any(), ScanDirection.BACKWARD, any()) }
                }
            }
        }

        given("a composite index with orderBy only partially matching its declared direction") {
            val compositeBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(mockk<BTree>()),
                    mapOf("composite_idx" to compositeHandle(compositeBtree)),
                    testReadLock,
                )

            `when`("scanning with col1 matching declared order but col2 not") {
                then("UnsupportedSortDirection is thrown") {
                    shouldThrow<TableException.UnsupportedSortDirection> {
                        table.selectByRange(
                            "composite_idx",
                            Bound(listOf(1L, 10L), isInclusive = true),
                            Bound(listOf(5L, 20L), isInclusive = true),
                            listOf(
                                ColumnOrder("col1", descending = false),
                                ColumnOrder("col2", descending = false),
                            ),
                        )
                    }
                }
            }
        }

        given("a composite index with more orderBy columns than free columns") {
            val compositeBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(mockk<BTree>()),
                    mapOf("composite_idx" to compositeHandle(compositeBtree)),
                    testReadLock,
                )

            `when`("scanning with an orderBy naming a column beyond col1/col2") {
                then("TooManyOrderColumns is thrown") {
                    shouldThrow<TableException.TooManyOrderColumns> {
                        table.selectByRange(
                            "composite_idx",
                            Bound(listOf(1L, 10L), isInclusive = true),
                            Bound(listOf(5L, 20L), isInclusive = true),
                            listOf(
                                ColumnOrder("col1", descending = false),
                                ColumnOrder("col2", descending = true),
                                ColumnOrder("col3", descending = false),
                            ),
                        )
                    }
                }
            }
        }

        given("a composite index with orderBy naming a free column out of position") {
            val compositeBtree = mockk<BTree>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(mockk<BTree>()),
                    mapOf("composite_idx" to compositeHandle(compositeBtree)),
                    testReadLock,
                )

            `when`("scanning with orderBy starting from col2, skipping col1") {
                then("OrderColumnMismatch is thrown") {
                    shouldThrow<TableException.OrderColumnMismatch> {
                        table.selectByRange(
                            "composite_idx",
                            Bound(listOf(1L, 10L), isInclusive = true),
                            Bound(listOf(5L, 20L), isInclusive = true),
                            listOf(ColumnOrder("col2", descending = true)),
                        )
                    }
                }
            }
        }

        given(
            "a prefix search that matches several rows with varied trailing column and row data"
        ) {
            val primaryBtree = mockk<BTree>()
            val compositeBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("composite_idx" to compositeHandle(compositeBtree)),
                    testReadLock,
                )

            // col1 = 1 fixed (the prefix), col2 varies — and each hit resolves to a different
            // primary row, including a row whose nullable email column is actually null, so the
            // whole extractData -> Row path is exercised with more than one hand-picked value.
            val hits =
                listOf(
                    Triple(30L, 101L, "a@x.com"),
                    Triple(20L, 102L, "b@x.com"),
                    Triple(10L, 103L, null),
                )
            val entries = hits.map { (col2, id, _) ->
                compositeKeySerializer.serialize(listOf(1L, col2)) to
                        compositeValueSerializer.serialize(listOf(id))
            }
            // The btree isn't only holding col1 = 1 data — a col1 = 2 row sits right after it in
            // byte order, standing in for "whatever comes next in the real tree". Without this,
            // the test can't tell a real stop-condition break from the cursor simply running out
            // of scripted entries on its own.
            val outOfPrefixId = 999L
            val outOfPrefixEntry =
                compositeKeySerializer.serialize(listOf(2L, 50L)) to
                        compositeValueSerializer.serialize(listOf(outOfPrefixId))
            every { cursor.step() } returnsMany (entries + outOfPrefixEntry + null)
            every { cursor.close() } just Runs
            every {
                compositeBtree.search(
                    eqBytes(compositeKeySerializer.serialize(listOf(1L))),
                    ScanDirection.FORWARD,
                    true,
                )
            } returns cursor
            for ((_, id, email) in hits) {
                every {
                    primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(id))))
                } returns primaryValueSerializer.serialize(listOf(id, email))
            }
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(outOfPrefixId))))
            } returns primaryValueSerializer.serialize(listOf(outOfPrefixId, "outside@x.com"))

            `when`("selecting by the col1 = 1 prefix") {
                val result = table.selectByPrefix("composite_idx", listOf(1L), emptyList())

                then(
                    "every row sharing the prefix comes back, each resolved through the primary index"
                ) {
                    result.map { it["id"] to it["email"] } shouldBe
                            listOf(101L to "a@x.com", 102L to "b@x.com", 103L to null)
                }
                then("the col1 = 2 row that follows the prefix in the tree is excluded") {
                    result.map { it["id"] } shouldNotContain outOfPrefixId
                    verify(exactly = 0) {
                        primaryBtree.search(
                            eqBytes(primaryKeySerializer.serialize(listOf(outOfPrefixId)))
                        )
                    }
                }
                then("both bounds are seeked/stopped as the same inclusive col1 = 1 value") {
                    verify {
                        compositeBtree.search(
                            eqBytes(compositeKeySerializer.serialize(listOf(1L))),
                            ScanDirection.FORWARD,
                            true,
                        )
                    }
                }
            }
        }

        given("an inclusive range scan over a primary index with limit and offset") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)

            val entryCount = 100
            val entries =
                (1L..entryCount).map {
                    primaryKeySerializer.serialize(listOf(it)) to
                            primaryValueSerializer.serialize(listOf(it, "$it@x.com"))
                }
            // Bound(null, ...) is unbounded on both sides, so takeWhile's bound check can never
            // stop the scan on its own - termination here depends entirely on step() eventually
            // returning null. returnsMany repeats its *last* value forever once the list runs out
            // instead of returning null, so the trailing null is required, not optional (without
            // it, a filter that only a few entries can ever satisfy - like "filter id=10" below -
            // makes drop()/take() pull forever past the 100 scripted entries: an infinite loop that
            // OOMs on the endlessly-allocated Row objects, not a hang that just times out).
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs
            every {
                primaryBtree.search(
                    null,
                    ScanDirection.FORWARD,
                    false,
                )
            } returns cursor

            `when`("scanning with limit 10") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        emptyList(),
                        null,
                        10,
                        null,
                    )
                then("only 10 items are returned, in order") {
                    result.map { it["id"] } shouldBe (1L..10L).toList()
                }
            }
            clearMocks(cursor)
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs


            `when`("scanning with limit 10 and offset 20") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        emptyList(),
                        null,
                        10,
                        20,
                    )
                then("the first 20 items are dropped and 10 items are returned") {
                    result.map { it["id"] } shouldBe (21L..30L).toList()
                }
            }
            clearMocks(cursor)
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs

            `when`("scanning with limit 10 and offset 20 and filter id=10") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        emptyList(),
                        { row -> (row["id"] as Long) == 10L },
                        10,
                        20,
                    )
                then("no item should be returned") {
                    result.isEmpty() shouldBe true
                }
            }
            clearMocks(cursor)
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs

            `when`("scanning with limit 10 and offset 20 and filter id=40") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        emptyList(),
                        { row -> (row["id"] as Long) == 40L },
                        10,
                        20,
                    )
                then("no item should be returned") {
                    result.isEmpty() shouldBe true
                }
            }

            clearMocks(cursor)
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs

            `when`("scanning with limit 10, no offset, and filter id=40") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        emptyList(),
                        { row -> (row["id"] as Long) == 40L },
                        10,
                        null,
                    )
                then("exactly one item is returned") {
                    result.isEmpty() shouldBe false
                    result.size shouldBe 1
                }
            }

            clearMocks(cursor)
            val descendingEntries =
                (entryCount downTo 1L).map {
                    primaryKeySerializer.serialize(listOf(it)) to
                            primaryValueSerializer.serialize(listOf(it, "$it@x.com"))
                }
            every { cursor.step() } returnsMany (descendingEntries + null)
            every { cursor.close() } just Runs
            every {
                primaryBtree.search(null, ScanDirection.BACKWARD, false)
            } returns cursor

            `when`("scanning backward (orderBy id descending) with limit 10") {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        listOf(ColumnOrder("id", descending = true)),
                        null,
                        10,
                        null,
                    )
                then("the 10 largest ids come back in descending order") {
                    result.map { it["id"] } shouldBe (100L downTo 91L).toList()
                }
            }

            clearMocks(cursor)
            every { cursor.step() } returnsMany (descendingEntries + null)
            every { cursor.close() } just Runs

            `when`(
                "scanning backward (orderBy id descending) with limit 5, offset 10, and filter id % 2 == 0"
            ) {
                val result =
                    table.selectByRange(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        listOf(ColumnOrder("id", descending = true)),
                        { row -> (row["id"] as Long) % 2 == 0L },
                        5,
                        10,
                    )
                then("the next 5 even ids after skipping the first 10 even ids, still descending") {
                    // Descending evens are 100, 98, 96, ..., 2 (50 total). Skipping the first 10
                    // (100 down to 82) leaves 80 as the next one, so the 5 taken are 80..72.
                    result.map { it["id"] } shouldBe listOf(80L, 78L, 76L, 74L, 72L)
                }
            }
        }

        given("an inclusive range scan over a secondary index with limit and offset") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )

            val entryCount = 30

            // Zero-padded so the email's byte-comparable order matches numeric id order too -
            // keeps the expected results below easy to compute by hand.
            fun emailFor(id: Long) = "user%02d@x.com".format(id)

            val entries =
                (1L..entryCount).map { id ->
                    secondaryKeySerializer.serialize(listOf(emailFor(id))) to
                            secondaryValueSerializer.serialize(listOf(id))
                }
            // Each secondary-index hit only carries the primary key as its value - extractData
            // takes a second hop through the primary index to resolve the full row, so every id
            // needs its own primaryBtree stub too (same two-step lookup as the composite-index
            // prefix search above).
            for (id in 1L..entryCount) {
                every {
                    primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(id))))
                } returns primaryValueSerializer.serialize(listOf(id, emailFor(id)))
            }
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs
            every {
                secondaryBtree.search(null, ScanDirection.FORWARD, false)
            } returns cursor

            `when`("scanning with limit 5 and offset 10") {
                val result =
                    table.selectByRange(
                        "email_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        emptyList(),
                        null,
                        5,
                        10,
                    )
                then("the 5 rows right after the first 10, resolved through the primary index") {
                    result.map { it["email"] } shouldBe (11L..15L).map { emailFor(it) }
                }
            }

            clearMocks(cursor)
            val descendingEntries =
                (entryCount downTo 1L).map { id ->
                    secondaryKeySerializer.serialize(listOf(emailFor(id))) to
                            secondaryValueSerializer.serialize(listOf(id))
                }
            every { cursor.step() } returnsMany (descendingEntries + null)
            every { cursor.close() } just Runs
            every {
                secondaryBtree.search(null, ScanDirection.BACKWARD, false)
            } returns cursor

            `when`(
                "scanning backward (orderBy email descending) with limit 5, offset 10, and filter on even id"
            ) {
                val result =
                    table.selectByRange(
                        "email_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        listOf(ColumnOrder("email", descending = true)),
                        { row -> (row["id"] as Long) % 2 == 0L },
                        5,
                        10,
                    )
                then("the next 5 even ids after skipping the first 10 even ids, still descending by email") {
                    // Descending ids are 30, 29, ..., 1; evens among them are 30, 28, ..., 2 (15
                    // total). Skipping the first 10 (30 down to 12) leaves 10 as the next one, so
                    // the 5 taken are 10, 8, 6, 4, 2.
                    result.map { it["id"] } shouldBe listOf(10L, 8L, 6L, 4L, 2L)
                }
            }
        }

        given("a full scan with limit and offset") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    emptyMap(),
                    testReadLock,
                )

            val entryCount = 100

            val entries =
                (1L..entryCount).map { id ->
                    primaryKeySerializer.serialize(listOf(id)) to
                            primaryValueSerializer.serialize(listOf(id, "$id@x.com"))
                }
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs
            every {
                primaryBtree.search(null, ScanDirection.FORWARD, false)
            } returns cursor

            `when`("scanning with limit 5 and offset 10") {
                val result = table.fullScan(emptyList(), null, 5, 10)
                then("the 5 rows right after the first 10, resolved through the primary index") {
                    result.map { it["id"] } shouldBe (11L..15L).toList()
                }
            }

            clearMocks(cursor)
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs

            `when`(
                "scanning forward with no limit, offset, filter, order"
            ) {
                val result =
                    table.fullScan(
                        emptyList(),
                        null,
                        null,
                        null,
                    )
                then("all rows ascending by id should be returned.") {
                    result.map { it["id"] } shouldBe (1L..100L).toList()
                }
            }

            clearMocks(cursor)
            every { cursor.step() } returnsMany (entries + null)
            every { cursor.close() } just Runs

            `when`(
                "scanning forward with no limit, offset, filter, orderBy ascending by id"
            ) {
                val result =
                    table.fullScan(
                        listOf(ColumnOrder("id", descending = false)),
                        null,
                        null,
                        null,
                    )
                then("all rows ascending by id should be returned.") {
                    result.map { it["id"] } shouldBe (1L..100L).toList()
                }
            }

            clearMocks(cursor)
            val descendingEntries =
                (entryCount downTo 1L).map { id ->
                    primaryKeySerializer.serialize(listOf(id)) to
                            primaryValueSerializer.serialize(listOf(id, "$id@x.com"))
                }
            every { cursor.step() } returnsMany (descendingEntries + null)
            every { cursor.close() } just Runs
            every {
                primaryBtree.search(null, ScanDirection.BACKWARD, false)
            } returns cursor

            `when`(
                "scanning backward with limit 5, offset 10, and filter on even id"
            ) {
                val result =
                    table.fullScan(
                        listOf(ColumnOrder("id", descending = true)),
                        { row -> (row["id"] as Long) % 2 == 0L },
                        5,
                        10,
                    )
                then("the next 5 even ids after skipping the first 10 even ids, still descending by id") {
                    result.map { it["id"] } shouldBe (80L downTo 72L).filter { value -> value % 2 == 0L }.toList()
                }
            }

            clearMocks(cursor)
            every { cursor.step() } returnsMany (descendingEntries + null)
            every { cursor.close() } just Runs
            `when`(
                "scanning backward with no limit, offset, filter"
            ) {
                val result =
                    table.fullScan(
                        listOf(ColumnOrder("id", descending = true)),
                        null,
                        null,
                        null,
                    )
                then("all rows descending by id should be returned.") {
                    result.map { it["id"] } shouldBe (100L downTo 1L).toList()
                }
            }

        }

        fun primaryEntry(id: Long) = primaryKeySerializer.serialize(listOf(id)) to
                primaryValueSerializer.serialize(listOf(id, "$id@x.com"))

        // deleteRow re-reads each row by primary key at delete time (and the secondary-index scan's
        // extractData does the same lookup), so both go through this one stub.
        fun stubPrimaryLookup(primaryBtree: BTree, id: Long) {
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(id))))
            } returns primaryValueSerializer.serialize(listOf(id, "$id@x.com"))
        }

        given("deleteWhere over the whole primary index with a filter matching some rows") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            val ids = 1L..5L
            every { cursor.step() } returnsMany (ids.map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            ids.forEach { stubPrimaryLookup(primaryBtree, it) }
            every { secondaryBtree.delete(any()) } just Runs
            every { primaryBtree.delete(any()) } just Runs

            `when`("deleting the rows with an even id") {
                val affected =
                    table.deleteWhere(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        filter = { row -> (row["id"] as Long) % 2 == 0L },
                    )

                then("the returned count is the number of rows actually deleted") {
                    affected shouldBe 2
                }
                then("only the matching rows are removed from the primary index") {
                    for (id in listOf(2L, 4L)) {
                        verify(exactly = 1) {
                            primaryBtree.delete(eqBytes(primaryKeySerializer.serialize(listOf(id))))
                        }
                    }
                    for (id in listOf(1L, 3L, 5L)) {
                        verify(exactly = 0) {
                            primaryBtree.delete(eqBytes(primaryKeySerializer.serialize(listOf(id))))
                        }
                    }
                }
                then("the matching rows are removed from the secondary index by their stored keys") {
                    for (id in listOf(2L, 4L)) {
                        verify(exactly = 1) {
                            secondaryBtree.delete(
                                eqBytes(secondaryKeySerializer.serialize(listOf("$id@x.com")))
                            )
                        }
                    }
                    verify(exactly = 2) { secondaryBtree.delete(any()) }
                }
            }
        }

        given("deleteWhere with a filter that matches nothing") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            every { cursor.step() } returnsMany ((1L..3L).map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor

            `when`("deleting") {
                val affected =
                    table.deleteWhere(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                        filter = { false },
                    )

                then("zero is returned, not an error") { affected shouldBe 0 }
                then("nothing is deleted from either index") {
                    verify(exactly = 0) { primaryBtree.delete(any()) }
                    verify(exactly = 0) { secondaryBtree.delete(any()) }
                }
            }
        }

        given("deleteWhere where a collected row is deleted by someone else before its turn") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)
            every { cursor.step() } returnsMany ((1L..3L).map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            stubPrimaryLookup(primaryBtree, 1L)
            stubPrimaryLookup(primaryBtree, 2L)
            // The scan above still saw id 3, but by the time deleteRow re-reads it, it is gone.
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(3L))))
            } returns null
            every { primaryBtree.delete(any()) } just Runs

            `when`("deleting everything") {
                val affected =
                    table.deleteWhere(
                        "pk_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                    )

                then("no error is thrown and the vanished row is not counted") {
                    affected shouldBe 2
                }
                then("the remaining rows are still deleted, and the vanished one is not touched") {
                    for (id in listOf(1L, 2L)) {
                        verify(exactly = 1) {
                            primaryBtree.delete(eqBytes(primaryKeySerializer.serialize(listOf(id))))
                        }
                    }
                    verify(exactly = 0) {
                        primaryBtree.delete(eqBytes(primaryKeySerializer.serialize(listOf(3L))))
                    }
                }
            }
        }

        given("deleteWhere driven by a secondary index scan") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                    testReadLock,
                )
            val ids = 1L..3L
            every { cursor.step() } returnsMany
                    (ids.map {
                        secondaryKeySerializer.serialize(listOf("$it@x.com")) to
                                secondaryValueSerializer.serialize(listOf(it))
                    } + null)
            every { cursor.close() } just Runs
            every { secondaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            ids.forEach { stubPrimaryLookup(primaryBtree, it) }
            every { secondaryBtree.delete(any()) } just Runs
            every { primaryBtree.delete(any()) } just Runs

            `when`("deleting every row found through email_idx") {
                val affected =
                    table.deleteWhere(
                        "email_idx",
                        Bound(null, isInclusive = true),
                        Bound(null, isInclusive = true),
                    )

                then("every scanned row is counted") { affected shouldBe 3 }
                then("each row is removed from both the secondary and the primary index") {
                    for (id in ids) {
                        verify(exactly = 1) {
                            secondaryBtree.delete(
                                eqBytes(secondaryKeySerializer.serialize(listOf("$id@x.com")))
                            )
                        }
                        verify(exactly = 1) {
                            primaryBtree.delete(eqBytes(primaryKeySerializer.serialize(listOf(id))))
                        }
                    }
                }
            }
        }

        given("deleteWhere on an unknown index name") {
            val primaryBtree = mockk<BTree>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)

            `when`("deleting") {
                then("UndefinedIndex is thrown before anything is deleted") {
                    shouldThrow<TableException.UndefinedIndex> {
                        table.deleteWhere(
                            "no_such_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                        )
                    }
                    verify(exactly = 0) { primaryBtree.delete(any()) }
                }
            }
        }

        given("updateWhere over the whole primary index with a filter matching some rows") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(
                rowSchema,
                primaryHandle(primaryBtree),
                mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                testReadLock,
            )
            val ids = 1L..5L
            every { cursor.step() } returnsMany (ids.map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            ids.forEach { stubPrimaryLookup(primaryBtree, it) }
            every { primaryBtree.update(any(), any(), any()) } just Runs
            for (id in listOf(2L, 4L)) {
                every { secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("new$id@x.com")))) } returns null
            }
            every { secondaryBtree.delete(any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("changing the email of rows with an even id") {
                val affected = table.updateWhere(
                    "pk_idx",
                    Bound(null, isInclusive = true),
                    Bound(null, isInclusive = true),
                    filter = { row -> (row["id"] as Long) % 2 == 0L },
                    assign = { row -> Row(rowSchema, listOf(row["id"], "new${row["id"]}@x.com")) },
                )

                then("the returned count is the number of rows actually updated") {
                    affected shouldBe 2
                }
                then("only the matching rows are rewritten in the primary index, with the assigned values") {
                    for (id in listOf(2L, 4L)) {
                        verify(exactly = 1) {
                            primaryBtree.update(
                                eqBytes(primaryKeySerializer.serialize(listOf(id))),
                                eqBytes(primaryKeySerializer.serialize(listOf(id))),
                                eqBytes(primaryValueSerializer.serialize(listOf(id, "new$id@x.com"))),
                            )
                        }
                    }
                    verify(exactly = 2) { primaryBtree.update(any(), any(), any()) }
                }
                then("each updated row's old secondary entry is replaced by one for the new email") {
                    for (id in listOf(2L, 4L)) {
                        verify(exactly = 1) {
                            secondaryBtree.delete(
                                eqBytes(secondaryKeySerializer.serialize(listOf("$id@x.com")))
                            )
                        }
                        verify(exactly = 1) {
                            secondaryBtree.insert(
                                eqBytes(secondaryKeySerializer.serialize(listOf("new$id@x.com"))),
                                eqBytes(secondaryValueSerializer.serialize(listOf(id))),
                            )
                        }
                    }
                }
            }
        }

        given("updateWhere with a filter that matches nothing") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)
            every { cursor.step() } returnsMany ((1L..3L).map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor

            `when`("updating") {
                val affected = table.updateWhere(
                    "pk_idx",
                    Bound(null, isInclusive = true),
                    Bound(null, isInclusive = true),
                    filter = { false },
                    assign = { row -> row },
                )

                then("zero is returned, not an error") { affected shouldBe 0 }
                then("nothing is written") {
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                }
            }
        }

        given("updateWhere where a collected row is deleted by someone else before its turn") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)
            every { cursor.step() } returnsMany ((1L..3L).map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            stubPrimaryLookup(primaryBtree, 1L)
            stubPrimaryLookup(primaryBtree, 2L)
            // The scan above still saw id 3, but by the time updateRow re-reads it, it is gone.
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(3L))))
            } returns null
            every { primaryBtree.update(any(), any(), any()) } just Runs

            `when`("updating everything") {
                val affected = table.updateWhere(
                    "pk_idx",
                    Bound(null, isInclusive = true),
                    Bound(null, isInclusive = true),
                    assign = { row -> Row(rowSchema, listOf(row["id"], "changed@x.com")) },
                )

                then("no error is thrown and the vanished row is not counted") {
                    affected shouldBe 2
                }
                then("the remaining rows are still updated, and the vanished one is not written") {
                    verify(exactly = 2) { primaryBtree.update(any(), any(), any()) }
                    verify(exactly = 0) {
                        primaryBtree.update(
                            eqBytes(primaryKeySerializer.serialize(listOf(3L))),
                            any(),
                            any(),
                        )
                    }
                }
            }
        }

        given("updateWhere whose assign changes the primary key") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)
            every { cursor.step() } returnsMany ((1L..2L).map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor

            `when`("updating") {
                then("PrimaryKeyUpdateNotSupported propagates and nothing is written") {
                    shouldThrow<TableException.PrimaryKeyUpdateNotSupported> {
                        table.updateWhere(
                            "pk_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                            assign = { row -> Row(rowSchema, listOf((row["id"] as Long) + 10, row["email"])) },
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                }
            }
        }

        given("updateWhere whose assign leaves a NOT NULL column null") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)
            every { cursor.step() } returnsMany ((1L..2L).map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor

            `when`("updating") {
                then("NotNullViolation propagates and nothing is written") {
                    shouldThrow<DatabaseException.NotNullViolation> {
                        table.updateWhere(
                            "pk_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                            assign = { row -> Row(rowSchema, listOf(null, row["email"])) },
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                }
            }
        }

        given("updateWhere whose assign violates a rule only for the last collected row") {
            val primaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)
            val ids = 1L..3L
            every { cursor.step() } returnsMany (ids.map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            // Stubbed so that a regression (rows 1 and 2 written before row 3 is rejected) shows up
            // as a failed verify below, not as an unrelated "no answer found" mock error.
            ids.forEach { stubPrimaryLookup(primaryBtree, it) }
            every { primaryBtree.update(any(), any(), any()) } just Runs

            `when`("the third row's replacement changes its primary key") {
                then("PrimaryKeyUpdateNotSupported is thrown and the two valid rows were not written either") {
                    shouldThrow<TableException.PrimaryKeyUpdateNotSupported> {
                        table.updateWhere(
                            "pk_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                            assign = { row ->
                                val id = row["id"] as Long
                                Row(rowSchema, listOf(if (id == 3L) 99L else id, "changed@x.com"))
                            },
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                }
            }

            clearMocks(cursor)
            every { cursor.step() } returnsMany (ids.map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs

            `when`("the third row's replacement has null in a NOT NULL column") {
                then("NotNullViolation is thrown and the two valid rows were not written either") {
                    shouldThrow<DatabaseException.NotNullViolation> {
                        table.updateWhere(
                            "pk_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                            assign = { row ->
                                val id = row["id"] as Long
                                Row(rowSchema, listOf(if (id == 3L) null else id, "changed@x.com"))
                            },
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                }
            }
        }

        given("a unique secondary index that already holds the email an update would move to") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table = Table(
                rowSchema,
                primaryHandle(primaryBtree),
                mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                testReadLock,
            )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "old@x.com"))
            every {
                secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("taken@x.com"))))
            } returns secondaryValueSerializer.serialize(listOf(2L))
            every { primaryBtree.update(any(), any(), any()) } just Runs
            every { secondaryBtree.delete(any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("updating row 1 to that email") {
                then("UniqueViolation is thrown before anything is written, to any index") {
                    shouldThrow<TableException.UniqueViolation> {
                        table.updateRow(
                            listOf(1L),
                            Row(rowSchema, listOf(1L, "taken@x.com"))
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                    verify(exactly = 0) { secondaryBtree.delete(any()) }
                    verify(exactly = 0) { secondaryBtree.insert(any(), any()) }
                }
            }
        }

        given("a non-unique secondary index whose key changes on update") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val table = Table(
                rowSchema,
                primaryHandle(primaryBtree),
                mapOf("email_idx" to secondaryHandle(secondaryBtree, isUnique = false)),
                testReadLock,
            )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "old@x.com"))
            every { primaryBtree.update(any(), any(), any()) } just Runs
            every { secondaryBtree.delete(any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("updating row 1 to an email another row may share") {
                table.updateRow(listOf(1L), Row(rowSchema, listOf(1L, "shared@x.com")))

                then("the new key is not looked up, since duplicates are allowed") {
                    verify(exactly = 0) { secondaryBtree.search(any()) }
                    verify(exactly = 1) { secondaryBtree.insert(any(), any()) }
                }
            }
        }

        given("updateWhere that would give two rows the same unique secondary key") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(
                rowSchema,
                primaryHandle(primaryBtree),
                mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                testReadLock,
            )
            val ids = 1L..2L
            every { cursor.step() } returnsMany (ids.map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            // Stubbed so a regression (row 1 written before row 2 is rejected) fails the verify
            // below instead of surfacing as an unrelated "no answer found" mock error.
            ids.forEach { stubPrimaryLookup(primaryBtree, it) }
            // Nobody in the index holds the shared email yet - only the batch conflicts with itself.
            every {
                secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("same@x.com"))))
            } returns null
            every { primaryBtree.update(any(), any(), any()) } just Runs
            every { secondaryBtree.delete(any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("setting every matching row's email to the same value") {
                then("UniqueViolation is thrown and not even the first row was written") {
                    shouldThrow<TableException.UniqueViolation> {
                        table.updateWhere(
                            "pk_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                            assign = { row -> Row(rowSchema, listOf(row["id"], "same@x.com")) },
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                    verify(exactly = 0) { secondaryBtree.insert(any(), any()) }
                }
            }
        }

        given("updateWhere where only the last row's new key is taken by a row outside the batch") {
            val primaryBtree = mockk<BTree>()
            val secondaryBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table = Table(
                rowSchema,
                primaryHandle(primaryBtree),
                mapOf("email_idx" to secondaryHandle(secondaryBtree)),
                testReadLock,
            )
            val ids = 1L..3L
            every { cursor.step() } returnsMany (ids.map { primaryEntry(it) } + null)
            every { cursor.close() } just Runs
            every { primaryBtree.search(null, ScanDirection.FORWARD, false) } returns cursor
            ids.forEach { stubPrimaryLookup(primaryBtree, it) }
            for (id in listOf(1L, 2L)) {
                every {
                    secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("new$id@x.com"))))
                } returns null
            }
            every {
                secondaryBtree.search(eqBytes(secondaryKeySerializer.serialize(listOf("new3@x.com"))))
            } returns secondaryValueSerializer.serialize(listOf(99L))
            every { primaryBtree.update(any(), any(), any()) } just Runs
            every { secondaryBtree.delete(any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("renaming every matching row's email") {
                then("UniqueViolation is thrown and the two rows before it were not written either") {
                    shouldThrow<TableException.UniqueViolation> {
                        table.updateWhere(
                            "pk_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                            assign = { row -> Row(rowSchema, listOf(row["id"], "new${row["id"]}@x.com")) },
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                    verify(exactly = 0) { secondaryBtree.insert(any(), any()) }
                }
            }
        }

        given("updateWhere on an unknown index name") {
            val primaryBtree = mockk<BTree>()
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap(), testReadLock)

            `when`("updating") {
                then("UndefinedIndex is thrown before anything is written") {
                    shouldThrow<TableException.UndefinedIndex> {
                        table.updateWhere(
                            "no_such_idx",
                            Bound(null, isInclusive = true),
                            Bound(null, isInclusive = true),
                            assign = { row -> row },
                        )
                    }
                    verify(exactly = 0) { primaryBtree.update(any(), any(), any()) }
                }
            }
        }
    })
