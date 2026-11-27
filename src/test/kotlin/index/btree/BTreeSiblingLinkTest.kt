package index.btree

import config.SimpleConfig
import config.StorageConfig
import helper.serializer.RowDataSerializerHelper
import index.serializer.MultiColumnKeySerializer
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * #78: leaves form a doubly linked list, and a backward walk follows the *left* links. Every split and merge has to
 * keep both directions in step, or a backward walk jumps over leaves.
 *
 * The check is black-box: a full BACKWARD scan must return exactly the reverse of a full FORWARD scan (which only
 * follows right links, so it's the reference). Entries are compared as (key, id) pairs, so even among duplicates a
 * skipped leaf shows up.
 *
 * The pool is small on purpose, so merges free pages that later splits reuse while evictions happen (#82).
 */
class BTreeSiblingLinkTest : BehaviorSpec({
    val config = SimpleConfig(StorageConfig(dbPath = "test-btree-sibling-link-${Uuid.random()}.db", poolSize = 32))
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

    fun newTree(): TypedBTree<RowRef> = TypedBTree(
        BTree("test", "test table", storageManager, config.indexConfig, INVALID_PAGE_ID),
        MultiColumnKeySerializer(IndexKeySchema(listOf(IndexColumn("v", ColumnType.INT, descending = false)))),
        RowDataSerializerHelper(serializer<RowRef>()),
    )

    /** Full walk in [direction], as (key, id) pairs. */
    fun TypedBTree<RowRef>.walk(direction: ScanDirection): List<Pair<Int, Int>> =
        scan(null, direction, boundGiven = false).map { (key, ref) -> (key.single() as Int) to ref.id }

    /**
     * Asserts both walks see exactly [expected] (sorted by key; ids among equal keys in any order), and that the
     * backward walk is the forward walk reversed. On failure, says where the backward walk first left the path.
     */
    fun TypedBTree<RowRef>.shouldWalkBothWays(expected: List<Pair<Int, Int>>) {
        val forward = walk(ScanDirection.FORWARD)
        val backward = walk(ScanDirection.BACKWARD)
        withClue("forward walk") {
            forward.map { it.first } shouldBe expected.map { it.first }.sorted()
            forward.sortedWith(compareBy({ it.first }, { it.second })) shouldBe
                expected.sortedWith(compareBy({ it.first }, { it.second }))
        }
        // Step i of the backward walk should be forward[last - i]; the first step where it isn't is the jump.
        val expectedBackward = forward.reversed()
        val jump = expectedBackward.indices.firstOrNull { it >= backward.size || backward[it] != expectedBackward[it] }
        withClue(
            "backward walk returned ${backward.size} of ${forward.size} entries. " +
                (jump?.let {
                    "After ${backward.getOrNull(it - 1) ?: "the start"} it should have reached ${expectedBackward[it]} " +
                        "but reached ${backward.getOrNull(it) ?: "the end"}."
                } ?: "")
        ) {
            backward shouldBe expectedBackward
        }
    }

    given("trees built by inserts only (splits)") {
        // ascending inserts only ever split the rightmost leaf, which has no right neighbour - the one order the
        // bug can't reach, so it doubles as a sanity check for the test itself.
        val orders: Map<String, (Int) -> List<Int>> = mapOf(
            "ascending" to { n -> (0 until n).toList() },
            "descending" to { n -> (0 until n).reversed().toList() },
            "shuffled" to { n -> (0 until n).shuffled(Random(78)) },
            "interleaved (evens, then odds)" to { n -> (0 until n).filter { it % 2 == 0 } + (0 until n).filter { it % 2 == 1 } },
        )
        for ((orderName, order) in orders) for (n in listOf(120, 300, 1000)) {
            `when`("$n distinct keys are inserted $orderName") {
                then("walking backward visits every leaf the forward walk does, in reverse") {
                    val tree = newTree()
                    val entries = order(n).mapIndexed { id, v -> v to id }
                    entries.forEach { (v, id) -> tree.insert(listOf(v), RowRef(id)) }
                    tree.shouldWalkBothWays(entries)
                }
            }
        }

        `when`("each key is inserted 200 times, in key order") {
            then("walking backward still visits every leaf (duplicates split middle leaves too)") {
                val tree = newTree()
                val entries = (0 until 10).flatMap { v -> List(200) { v } }.mapIndexed { id, v -> v to id }
                entries.forEach { (v, id) -> tree.insert(listOf(v), RowRef(id)) }
                tree.shouldWalkBothWays(entries)
            }
        }
    }

    given("trees that lose most of their keys (merges)") {
        // Built in ascending order, so before the deletes the links are intact (see above) and only merges can break them.
        val n = 300
        val patterns: Map<String, (Int) -> Boolean> = mapOf(
            "the first three quarters" to { v -> v < n * 3 / 4 },
            "the last three quarters" to { v -> v >= n / 4 },
            "the middle half" to { v -> v in n / 4 until n * 3 / 4 },
            "three of every four" to { v -> v % 4 != 0 },
            "a random three quarters" to { v -> Random(v * 31 + 78).nextInt(4) != 0 },
        )
        for ((patternName, isDeleted) in patterns) {
            `when`("$patternName of $n keys are deleted") {
                then("walking backward visits every remaining leaf, in reverse") {
                    val tree = newTree()
                    (0 until n).forEach { v -> tree.insert(listOf(v), RowRef(v)) }
                    (0 until n).filter(isDeleted).forEach { v -> tree.delete(listOf(v)) }
                    tree.shouldWalkBothWays((0 until n).filterNot(isDeleted).map { it to it })
                }
            }
        }

        `when`("keys are deleted and then inserted again, over several rounds")  {
            then("walking backward stays consistent after every round") {
                val tree = newTree()
                val present = sortedSetOf<Int>()
                val random = Random(780)
                repeat(6) { round ->
                    repeat(250) {
                        val v = random.nextInt(600)
                        if (present.add(v)) tree.insert(listOf(v), RowRef(v))
                    }
                    present.filter { random.nextInt(3) != 0 }.forEach { v -> tree.delete(listOf(v)); present.remove(v) }
                    withClue("after round $round") { tree.shouldWalkBothWays(present.map { it to it }) }
                }
            }
        }
    }
})
