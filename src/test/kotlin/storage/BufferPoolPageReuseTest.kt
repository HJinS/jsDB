package storage

import config.SimpleConfig
import config.StorageConfig
import exception.StorageEngineException
import helper.shouldThrowCode
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import storageEngine.BufferPoolManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.MetaPageManager
import storageEngine.StorageManager
import storageEngine.lru.FrameNodePolicy
import util.PageType
import storageEngine.page.SlottedPage
import util.ErrorCode
import util.LockMode
import java.io.File
import kotlin.uuid.Uuid

/**
 * #82: a page id must never live in two frames at once. `newPage(id)` used to take a fresh frame and point
 * `pageTable[id]` at it even when `id` was still cached in another frame - which happens every time a freed page is
 * reused, since `FreeSpaceManager.getFreePageID` reads the freed page (for the free list's next pointer) right before
 * handing its id to `newPage`. Evicting that stale frame later removed `pageTable[id]` - the *new* frame's mapping - so
 * unpinning the page in use threw `PAGE_NOT_FOUND_IN_CACHE`.
 *
 * Uses a real [DiskManager] and a tiny pool, so evictions actually happen.
 */
class BufferPoolPageReuseTest : BehaviorSpec({
    val files = mutableListOf<File>()
    afterSpec { files.forEach { it.delete() } }

    /** A fresh pool of [poolSize] frames over a fresh file. */
    fun newPool(poolSize: Int): Pair<BufferPoolManager, DiskManager> {
        val config = SimpleConfig(StorageConfig(dbPath = "test-page-reuse-${Uuid.random()}.db", poolSize = poolSize))
        files += File(config.storageConfig.dbPath)
        val diskManager = DiskManager(config.storageConfig, config.indexConfig)
        val pool = BufferPoolManager(
            diskManager,
            FrameNodePolicy(config.storageConfig.midPointLruConfig),
            config.indexConfig,
            poolSize,
        )
        return pool to diskManager
    }

    val markerOffset = 64

    fun BufferPoolManager.write(pageId: Long, marker: Int, newPage: Boolean = false) {
        val lock = if (newPage) newPage(pageId) else fetchPage(pageId, LockMode.WRITE)
        lock.asWriteView { it.putInt(markerOffset, marker) }
        lock.close()
    }

    fun BufferPoolManager.read(pageId: Long): Int =
        fetchPage(pageId, LockMode.READ).use { lock -> lock.asReadView { it.getInt(markerOffset) } }

    /** Pages 1..[count] on disk (marker = page id), then nothing pinned. */
    fun BufferPoolManager.prepare(count: Int) {
        for (id in 1L..count) {
            write(id, id.toInt(), newPage = true)
            flushPage(id)
        }
    }

    /** Touches every page in [ids] once, so an LRU pool smaller than that evicts whatever was cached before. */
    fun BufferPoolManager.churn(ids: LongRange) = ids.forEach { read(it) }

    given("a page whose old copy is still cached when newPage is called for it again") {
        `when`("the old copy is evicted while the new page is still in use") {
            then("the new page can still be unpinned") {
                val (pool, disk) = newPool(poolSize = 3)
                pool.prepare(6)
                pool.read(5)                                  // old copy of 5 cached, unpinned
                val newLock = pool.newPage(5)                 // 5 again, as when a freed page is reused
                newLock.asWriteView { it.putInt(markerOffset, 555) }
                pool.churn(1L..4L)                            // evicts the old copy
                shouldNotThrowAny { newLock.close() }         // was PAGE_NOT_FOUND_IN_CACHE
                pool.churn(1L..4L)
                pool.read(5) shouldBe 555
                disk.close()
            }
        }

        `when`("the old copy was dirty")  {
            then("its contents never reach disk over the new page") {
                val (pool, disk) = newPool(poolSize = 3)
                pool.prepare(6)
                pool.write(5, 111)                            // old copy of 5: dirty, unpinned
                pool.write(5, 555, newPage = true)
                repeat(3) { pool.churn(1L..4L) }              // evicts everything, in whatever order
                pool.read(5) shouldBe 555
                disk.close()
            }
        }

        `when`("the page is still pinned by someone else") {
            then("newPage refuses it with PAGE_IN_USE instead of handing out a second frame") {
                val (pool, disk) = newPool(poolSize = 3)
                pool.prepare(6)
                val inUse = pool.fetchPage(5, LockMode.READ)
                shouldThrowCode<StorageEngineException>(ErrorCode.PAGE_IN_USE) { pool.newPage(5) }
                inUse.close()
                disk.close()
            }
        }
    }

    given("a page freed through StorageManager and handed out again") {
        `when`("the pool is small enough that pages get evicted while the reused page is in use") {
            then("the reused page works like any new page") {
                val config = SimpleConfig(StorageConfig(dbPath = "test-page-reuse-${Uuid.random()}.db", poolSize = 4))
                files += File(config.storageConfig.dbPath)
                val disk = DiskManager(config.storageConfig, config.indexConfig)
                val pool = BufferPoolManager(disk, FrameNodePolicy(config.storageConfig.midPointLruConfig), config.indexConfig, 4)
                MetaPageManager(pool).initialize()
                val storage = StorageManager(FreeSpaceManager(pool), pool, config.indexConfig)

                val ids = List(3) { storage.newPage(PageType.LEAF_NODE, LockMode.WRITE).use { it.pageId } }
                storage.deletePage(ids[1])

                val reused = storage.newPage(PageType.LEAF_NODE, LockMode.WRITE)
                reused.pageId shouldBe ids[1]                 // the free list hands the freed page back
                repeat(4) { storage.newPage(PageType.LEAF_NODE, LockMode.WRITE).close() }  // evictions
                shouldNotThrowAny { reused.close() }

                storage.fetchPage(ids[1], LockMode.READ).use { lock ->
                    lock.asReadView { SlottedPage(config.indexConfig, ids[1], it).type } shouldBe PageType.LEAF_NODE
                }
                disk.close()
            }
        }
    }
})
