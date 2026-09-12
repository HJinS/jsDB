package database

import exception.TableException
import index.btree.BTree
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import schema.ColumnType
import schema.IndexColumn
import schema.IndexHandle
import schema.IndexRow
import schema.Row
import schema.RowColumn
import schema.RowSchema

class TableTest: BehaviorSpec({
    val rowSchema = RowSchema(listOf(
        RowColumn("id", ColumnType.LONG, false, 0),
        RowColumn("email", ColumnType.STRING, true, null)
    ))

    fun primaryHandle(btree: BTree<List<Any?>, List<Any?>>) = IndexHandle(
        IndexRow(
            indexId = 0L,
            indexName = "pk_idx",
            tableName = "users",
            rootPageId = null,
            isPrimary = true,
            isUnique = true,
            keyColumns = listOf(IndexColumn("id", ColumnType.LONG, false))
        ),
        btree
    )

    fun secondaryHandle(btree: BTree<List<Any?>, List<Any?>>, isUnique: Boolean = true) = IndexHandle(
        IndexRow(
            indexId = 1L,
            indexName = "email_idx",
            tableName = "users",
            rootPageId = null,
            isPrimary = false,
            isUnique = isUnique,
            keyColumns = listOf(IndexColumn("email", ColumnType.STRING, false))
        ),
        btree
    )

    given("a table with an empty primary index and a unique secondary index"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { primaryBtree.search(listOf(1L)) } returns null
        every { secondaryBtree.search(listOf("a@x.com")) } returns null
        every { primaryBtree.insert(any(), any()) } just Runs
        every { secondaryBtree.insert(any(), any()) } just Runs

        `when`("inserting a new row"){
            table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))

            then("the primary index should store the full row"){
                verify { primaryBtree.insert(listOf(1L), listOf(1L, "a@x.com")) }
            }
            then("the secondary index should store the primary key"){
                verify { secondaryBtree.insert(listOf("a@x.com"), listOf(1L)) }
            }
        }
    }

    given("a table whose primary index already has that key"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { primaryBtree.search(listOf(1L)) } returns listOf(1L, "old@x.com")

        `when`("inserting a row with that primary key"){
            then("UniqueViolation should be thrown"){
                shouldThrow<TableException.UniqueViolation> {
                    table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))
                }
            }
        }
    }

    given("a table whose unique secondary index already has that key"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { primaryBtree.search(listOf(1L)) } returns null
        every { secondaryBtree.search(listOf("a@x.com")) } returns listOf(99L)

        `when`("inserting a row with that secondary key"){
            then("UniqueViolation should be thrown"){
                shouldThrow<TableException.UniqueViolation> {
                    table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))
                }
            }
        }
    }

    given("a table with a non-unique secondary index that already has that key"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree, isUnique = false))
        )
        every { primaryBtree.search(listOf(1L)) } returns null
        every { primaryBtree.insert(any(), any()) } just Runs
        every { secondaryBtree.insert(any(), any()) } just Runs

        `when`("inserting a row with that secondary key"){
            table.insertRow(Row(rowSchema, listOf(1L, "a@x.com")))

            then("no existence check should happen and the row should just be inserted"){
                verify(exactly = 0) { secondaryBtree.search(any()) }
                verify { secondaryBtree.insert(listOf("a@x.com"), listOf(1L)) }
            }
        }
    }

    given("a table with a row stored at a key"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            emptyMap()
        )
        every { primaryBtree.search(listOf(1L)) } returns listOf(1L, "a@x.com")

        `when`("selecting by that key"){
            then("the row should be returned"){
                table.selectByKey(listOf(1L)).shouldNotBeNull {
                    this["email"] shouldBe "a@x.com"
                }
            }
        }
    }

    given("a table with no row at a key"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            emptyMap()
        )
        every { primaryBtree.search(listOf(999L)) } returns null

        `when`("selecting by that key"){
            then("null should be returned"){
                table.selectByKey(listOf(999L)) shouldBe null
            }
        }
    }

    given("a table with a secondary index whose key resolves to a stored primary key"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { secondaryBtree.search(listOf("a@x.com")) } returns listOf(1L)
        every { primaryBtree.search(listOf(1L)) } returns listOf(1L, "a@x.com")

        `when`("selecting via that secondary index"){
            then("the row should be found via the two-step lookup"){
                table.selectByIndex("email_idx", listOf("a@x.com")).shouldNotBeNull {
                    this["id"] shouldBe 1L
                }
            }
        }
    }

    given("a table with a secondary index whose key does not resolve to anything"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { secondaryBtree.search(listOf("nope@x.com")) } returns null

        `when`("selecting via that secondary index"){
            then("null should be returned"){
                table.selectByIndex("email_idx", listOf("nope@x.com")) shouldBe null
            }
        }
    }

    given("a table without a secondary index of a given name"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            emptyMap()
        )

        `when`("selecting via that unknown index name"){
            then("UndefinedIndex should be thrown"){
                shouldThrow<TableException.UndefinedIndex> {
                    table.selectByIndex("no-such-index", listOf("x"))
                }
            }
        }
    }

    given("a table with no row at the primary key being updated"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            emptyMap()
        )
        every { primaryBtree.search(listOf(1L)) } returns null

        `when`("updating that row"){
            then("RowNotFound should be thrown"){
                shouldThrow<TableException.RowNotFound> {
                    table.updateRow(Row(rowSchema, listOf(1L, "new@x.com")))
                }
            }
        }
    }

    given("a table with a row whose secondary key stays the same after update"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { primaryBtree.search(listOf(1L)) } returns listOf(1L, "a@x.com")
        every { primaryBtree.update(any(), any(), any()) } just Runs
        every { secondaryBtree.update(any(), any(), any()) } just Runs

        `when`("updating that row"){
            table.updateRow(Row(rowSchema, listOf(1L, "a@x.com")))

            then("the secondary index should be updated in place, not deleted and reinserted"){
                verify { secondaryBtree.update(listOf("a@x.com"), listOf("a@x.com"), listOf(1L)) }
                verify(exactly = 0) { secondaryBtree.delete(any()) }
                verify(exactly = 0) { secondaryBtree.insert(any(), any()) }
            }
        }
    }

    given("a table with a row whose secondary key changes after update"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { primaryBtree.search(listOf(1L)) } returns listOf(1L, "old@x.com")
        every { primaryBtree.update(any(), any(), any()) } just Runs
        every { secondaryBtree.delete(any()) } just Runs
        every { secondaryBtree.insert(any(), any()) } just Runs

        `when`("updating that row"){
            table.updateRow(Row(rowSchema, listOf(1L, "new@x.com")))

            then("the old secondary entry should be deleted and a new one inserted"){
                verify { secondaryBtree.delete(listOf("old@x.com")) }
                verify { secondaryBtree.insert(listOf("new@x.com"), listOf(1L)) }
            }
        }
    }

    given("a table with no row at the primary key being deleted"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(rowSchema, primaryHandle(primaryBtree), emptyMap())
        every { primaryBtree.search(listOf(1L)) } returns null

        `when`("deleting that row"){
            then("RowNotFound should be thrown"){
                shouldThrow<TableException.RowNotFound> { table.deleteRow(listOf(1L)) }
            }
        }
    }

    given("a table with a row to delete"){
        val primaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val secondaryBtree = mockk<BTree<List<Any?>, List<Any?>>>()
        val table = Table(
            rowSchema,
            primaryHandle(primaryBtree),
            mapOf("email_idx" to secondaryHandle(secondaryBtree))
        )
        every { primaryBtree.search(listOf(1L)) } returns listOf(1L, "a@x.com")
        every { secondaryBtree.delete(any()) } just Runs
        every { primaryBtree.delete(any()) } just Runs

        `when`("deleting that row"){
            table.deleteRow(listOf(1L))

            then("the row should be removed from the secondary index using its stored key"){
                verify { secondaryBtree.delete(listOf("a@x.com")) }
            }
            then("the row should be removed from the primary index"){
                verify { primaryBtree.delete(listOf(1L)) }
            }
        }
    }
})
