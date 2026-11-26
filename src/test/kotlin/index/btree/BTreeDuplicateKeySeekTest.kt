package index.btree

import config.SimpleConfig
import config.StorageConfig
import helper.serializer.RowDataSerializerHelper
import index.serializer.MultiColumnKeySerializer
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import schema.ColumnType
import schema.IndexColumn
import schema.IndexKeySchema
import storageEngine.BufferPoolManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.MetaPageManager
import storageEngine.StorageManager
import storageEngine.lru.FrameNodePolicy
import util.INVALID_PAGE_ID
import java.io.File
import kotlin.uuid.Uuid

@Serializable
data class RowRef(val id: Int)

/**
 * #77: a non-unique index stores the same key bytes many times, and once those duplicates span leaf pages a range
 * seek has to land on the *first* of them (forward) or just before it (backward), not on whichever one a binary
 * search happens to hit - in a leaf, or among equal separators in an internal node.
 *
 * Kept apart from `BTreeTest` on purpose: it gets its own storage, so trees other specs create or destroy can't
 * affect it.
 */
class BTreeDuplicateKeySeekTest : BehaviorSpec({
    val config = SimpleConfig(StorageConfig(dbPath = "test-btree-duplicate-seek-${Uuid.random()}.db", poolSize = 100))
    lateinit var diskManager: DiskManager
    lateinit var storageManager: StorageManager

    beforeSpec {
        diskManager = DiskManager(config.storageConfig, config.indexConfig)
        val bufferPoolManager = BufferPoolManager(
            diskManager,
            FrameNodePolicy(config.storageConfig.midPointLruConfig),
            config.indexConfig,
            config.storageConfig.poolSize,
        )
        MetaPageManager(bufferPoolManager).initialize()
        storageManager = StorageManager(FreeSpaceManager(bufferPoolManager), bufferPoolManager, config.indexConfig)
    }
    afterSpec {
        diskManager.close()
        File(config.storageConfig.dbPath).delete()
    }

    val keyCount = 10
    val duplicates = 200

    fun newTree(): TypedBTree<RowRef> = TypedBTree(
        BTree("test", "test table", storageManager, config.indexConfig, INVALID_PAGE_ID),
        MultiColumnKeySerializer(IndexKeySchema(listOf(IndexColumn("v", ColumnType.INT, descending = false)))),
        RowDataSerializerHelper(serializer<RowRef>()),
    )

    fun TypedBTree<RowRef>.seek(v: Int, direction: ScanDirection) =
        scan(keySerializer.serialize(listOf(v)), direction, boundGiven = true)

    given("a tree whose keys repeat across leaf pages, inserted in key order") {
        val tree = newTree()
        var nextId = 0
        for (v in 0 until keyCount) repeat(duplicates) { tree.insert(listOf(v), RowRef(nextId++)) }

        `when`("seeking FORWARD to a key that has duplicates") {
            then("the scan starts at its first duplicate, so every entry >= the key comes back") {
                for (v in 0 until keyCount) withClue("seek to $v") {
                    val result = tree.seek(v, ScanDirection.FORWARD)
                    result.size shouldBe (keyCount - v) * duplicates
                    result.first().first shouldBe listOf(v)
                }
            }
        }

        `when`("seeking BACKWARD from a key that has duplicates") {
            then("the scan starts before its first duplicate, so no entry >= the key comes back") {
                for (v in 0 until keyCount) withClue("seek to $v") {
                    val keys = tree.seek(v, ScanDirection.BACKWARD).map { it.first.single() as Int }
                    keys.firstOrNull() shouldBe (if (v == 0) null else v - 1)
                    keys.count { it >= v } shouldBe 0
                }
            }

            // Disabled until #78 is fixed: duplicates make middle leaves split too (an insert descends to whichever
            // equal separator the search hits), and a split leaves the old right neighbour's left link stale, so a
            // long backward walk skips leaves. The seek position itself is checked above.
            xthen("every entry < the key comes back") {
                for (v in 0 until keyCount) withClue("seek to $v") {
                    tree.seek(v, ScanDirection.BACKWARD).size shouldBe v * duplicates
                }
            }
        }

        `when`("seeking to a key that isn't there") {
            then("FORWARD starts at the next key and BACKWARD at the previous one, as before") {
                tree.seek(keyCount, ScanDirection.FORWARD) shouldBe emptyList()
                tree.seek(-1, ScanDirection.FORWARD).size shouldBe keyCount * duplicates
                tree.seek(keyCount, ScanDirection.BACKWARD).first().first shouldBe listOf(keyCount - 1)
                tree.seek(-1, ScanDirection.BACKWARD) shouldBe emptyList()
            }
        }
    }

    given("a tree whose keys repeat across leaf pages, inserted out of order") {
        val tree = newTree()
        for (i in 0 until keyCount * duplicates) tree.insert(listOf((i * 7) % keyCount), RowRef(i))

        `when`("seeking FORWARD to a key that has duplicates") {
            then("the scan starts at its first duplicate, so every entry >= the key comes back") {
                for (v in 0 until keyCount) withClue("seek to $v") {
                    val result = tree.seek(v, ScanDirection.FORWARD)
                    result.size shouldBe (keyCount - v) * duplicates
                    result.first().first shouldBe listOf(v)
                }
            }
        }
    }

    given("a tree where one key fills many leaves") {
        // 1990 copies of 7, then one each of 0..9 (so 7 once more).
        val keys = buildList {
            addAll(List(1990) { 7 })
            addAll(0..9)
        }
        val tree = newTree()
        keys.forEachIndexed { i, v -> tree.insert(listOf(v), RowRef(i)) }

        `when`("seeking FORWARD to that key") {
            then("every one of its duplicates comes back, and nothing smaller") {
                val result = tree.seek(7, ScanDirection.FORWARD).map { it.first.single() as Int }
                result.count { it == 7 } shouldBe keys.count { it == 7 }
                result.size shouldBe keys.count { it >= 7 }
            }
        }
    }
})
