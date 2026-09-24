package storage

import config.MidpointLruConfig
import config.SimpleConfig
import config.StorageConfig
import index.btree.node.Node
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import storageEngine.BufferPoolManager
import storageEngine.MetaPageManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.StorageManager
import exception.StorageEngineException
import storageEngine.lru.FrameNodePolicy
import storageEngine.page.SlottedPage
import util.LockMode
import util.PageType
import java.io.File
import kotlin.uuid.Uuid

class StorageManagerTest: BehaviorSpec({
    afterSpec {
        diskManager.close()
        val file = File(config.storageConfig.dbPath)
        file.delete()
    }

    given("storage manager"){
        metaPageManager.initialize()
        `when`("get new page with ${PageType.LEAF_NODE} and ${LockMode.READ}"){
            val expectedNewPageId = 1L
            val newPageLock = storageManager.newPage(PageType.LEAF_NODE, LockMode.READ)
            then("the page id should be $expectedNewPageId because there is no used free space"){
                newPageLock.pageId shouldBe expectedNewPageId
            }
            then("the page type should be ${PageType.LEAF_NODE}") {
                newPageLock.asReadView { buffer ->
                    val newPage = SlottedPage(indexConfig, newPageLock.pageId, buffer)
                    val newNode = Node.from(indexConfig, newPage)
                    newNode.page.type shouldBe PageType.LEAF_NODE
                }
                newPageLock.close()
            }

        }
        `when`("fetch pageId 0L"){
            then("should throw InvalidPageIdException"){
                shouldThrow<StorageEngineException.InvalidPageId> { storageManager.fetchPage(0L, LockMode.READ) }
            }
        }
        `when`("fetch pageId 1L"){
            val pageLock = storageManager.fetchPage(1L, LockMode.READ)
            then("should should return the correct pageLock"){
                pageLock.asReadView { buffer ->
                    val page = SlottedPage(indexConfig, pageLock.pageId, buffer)
                    val node = Node.from(indexConfig, page)
                    node.page.type shouldBe PageType.LEAF_NODE
                    node.page.pageId shouldBe 1L
                }
                pageLock.close()
            }
        }
        `when`("delete page 0L"){
            then("should throw InvalidPageIdException"){
                shouldThrow<StorageEngineException.InvalidPageId> { storageManager.deletePage(0L) }
            }
        }

        `when`("delete pageId 1L"){
            then("page 1L should be deleted and 1L should be registered as free space"){
                storageManager.deletePage(1L)
            }
        }

        `when`("get new page"){
            val newPageLock = storageManager.newPage(PageType.LEAF_NODE, LockMode.READ)
            val expectedNewPageId = 1L
            then("the page id should be $expectedNewPageId because there is no used free space"){
                newPageLock.pageId shouldBe expectedNewPageId
            }
            then("the page type should be ${PageType.LEAF_NODE}") {
                newPageLock.asReadView { buffer ->
                    val newPage = SlottedPage(indexConfig, newPageLock.pageId, buffer)
                    val newNode = Node.from(indexConfig, newPage)
                    newNode.page.type shouldBe PageType.LEAF_NODE
                }
                newPageLock.close()
            }
        }

        // issue #58, checklist item 3: fetching a page whose type isn't a live B+Tree node
        // (INTERNAL_NODE/LEAF_NODE) must not leak the pin/lock bufferPoolManager.fetchPage already
        // acquired before the type check ran.
        `when`("fetch a page whose type is neither ${PageType.INTERNAL_NODE} nor ${PageType.LEAF_NODE}"){
            val invalidPageId = 999L
            val rawPageLock = bufferPoolManager.newPage(invalidPageId)
            rawPageLock.asWriteView { buffer ->
                // initData() alone leaves the page's type at PageType.EMPTY - never overwritten
                // with INTERNAL_NODE/LEAF_NODE, so it's an invalid page for StorageManager.
                SlottedPage(indexConfig, invalidPageId, buffer).initData()
            }
            rawPageLock.close()
            then("should throw InvalidPageType without leaking the underlying pin/lock"){
                shouldThrow<StorageEngineException.InvalidPageType> {
                    storageManager.fetchPage(invalidPageId, LockMode.READ)
                }
                // If the pin/lock had leaked, this fresh fetch would see pinCount 2 (the leaked
                // pin plus this call's own) instead of a clean 1.
                val retryLock = bufferPoolManager.fetchPage(invalidPageId, LockMode.READ)
                retryLock.frame.pinCount.get() shouldBe 1
                retryLock.frame.latch.isWriteLocked shouldBe false
                retryLock.close()
                retryLock.frame.pinCount.get() shouldBe 0
            }
        }
    }
}){
    companion object {
        private val config = SimpleConfig(
            storageConfig = StorageConfig(
                dbPath = "./js-test-storage-manager-${Uuid.random()}.db",
                poolSize = 20,
                midPointLruConfig = MidpointLruConfig(capacity = 20)
            )
        )
        val indexConfig = config.indexConfig
        private val diskManager = DiskManager(config.storageConfig, config.indexConfig)
        private val replacer = FrameNodePolicy(config.storageConfig.midPointLruConfig)
        private val bufferPoolManager = BufferPoolManager(diskManager, replacer, config.indexConfig, config.storageConfig.poolSize)
        private val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
        private val storageManager = StorageManager(freeSpaceManager, bufferPoolManager, config.indexConfig)
        private val metaPageManager = MetaPageManager(bufferPoolManager)
    }
}