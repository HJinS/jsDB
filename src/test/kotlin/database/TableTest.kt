package database

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
import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
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
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns null

            `when`("updating that row") {
                then("RowNotFound should be thrown") {
                    shouldThrow<TableException.RowNotFound> {
                        table.updateRow(Row(rowSchema, listOf(1L, "new@x.com")))
                    }
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
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "a@x.com"))
            every { primaryBtree.update(any(), any(), any()) } just Runs
            every { secondaryBtree.update(any(), any(), any()) } just Runs

            `when`("updating that row") {
                table.updateRow(Row(rowSchema, listOf(1L, "a@x.com")))

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
                )
            every {
                primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(1L))))
            } returns primaryValueSerializer.serialize(listOf(1L, "old@x.com"))
            every { primaryBtree.update(any(), any(), any()) } just Runs
            every { secondaryBtree.delete(any()) } just Runs
            every { secondaryBtree.insert(any(), any()) } just Runs

            `when`("updating that row") {
                table.updateRow(Row(rowSchema, listOf(1L, "new@x.com")))

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
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap())
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
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap())

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
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap())

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
            val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap())

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
            val table = Table(rowSchema, primaryHandle(mockk<BTree>()), emptyMap())

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

        given("a prefix search that matches several rows with varied trailing column and row data") {
            val primaryBtree = mockk<BTree>()
            val compositeBtree = mockk<BTree>()
            val cursor = mockk<Cursor>()
            val table =
                Table(
                    rowSchema,
                    primaryHandle(primaryBtree),
                    mapOf("composite_idx" to compositeHandle(compositeBtree)),
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
            val entries =
                hits.map { (col2, id, _) ->
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

            `when`("selecting by the col1 = 1 prefix"){
                val result = table.selectByPrefix("composite_idx", listOf(1L), emptyList())

                then("every row sharing the prefix comes back, each resolved through the primary index"){
                    result.map { it["id"] to it["email"] } shouldBe
                        listOf(101L to "a@x.com", 102L to "b@x.com", 103L to null)
                }
                then("the col1 = 2 row that follows the prefix in the tree is excluded"){
                    result.map { it["id"] } shouldNotContain outOfPrefixId
                    verify(exactly = 0) {
                        primaryBtree.search(eqBytes(primaryKeySerializer.serialize(listOf(outOfPrefixId))))
                    }
                }
                then("both bounds are seeked/stopped as the same inclusive col1 = 1 value"){
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
    })
