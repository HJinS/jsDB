package storage

import config.IndexConfig
import config.MidpointLruConfig
import config.StorageConfig
import index.btree.node.Node
import index.serializer.MultiColumnKeySerializer
import index.util.Column
import index.util.ColumnType
import index.util.KeySchema
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import storageEngine.BufferPoolManager
import storageEngine.DatabaseInitializer
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.StorageManager
import storageEngine.exception.StorageManagerException
import storageEngine.lru.MidpointLRUPolicy
import storageEngine.page.SlottedPage
import storageEngine.util.LockMode
import storageEngine.util.PageType
import java.io.File

class StorageManagerTest: BehaviorSpec({
    afterSpec {
        diskManager.close()
        val file = File(storageConfig.dbPath)
        file.delete()
    }

    given("storage manager"){
        databaseInitializer.initMetaPage()
        `when`("get new page with ${PageType.LEAF_NODE} and ${LockMode.READ}"){
            val expectedNewPageId = 1L
            val newPageLock = storageManager.newPage(PageType.LEAF_NODE, LockMode.READ)
            then("the page id should be $expectedNewPageId because there is no used free space"){
                newPageLock.pageId shouldBe expectedNewPageId
            }
            then("the page type should be ${PageType.LEAF_NODE}") {
                newPageLock.asReadView { buffer ->
                    val newPage = SlottedPage(indexConfig, newPageLock.pageId, buffer)
                    val newNode = Node.from(indexConfig, newPage, keySerializer)
                    newNode.page.type shouldBe PageType.LEAF_NODE
                }
                newPageLock.close()
            }

        }
        `when`("fetch pageId 0L"){
            then("should throw InvalidPageIdException"){
                shouldThrow<StorageManagerException.InvalidPageIdException> { storageManager.fetchPage(0L, LockMode.READ) }
            }
        }
        `when`("fetch pageId 1L"){
            val pageLock = storageManager.fetchPage(1L, LockMode.READ)
            then("should should return the correct pageLock"){
                pageLock.asReadView { buffer ->
                    val page = SlottedPage(indexConfig, pageLock.pageId, buffer)
                    val node = Node.from(indexConfig, page, keySerializer)
                    node.page.type shouldBe PageType.LEAF_NODE
                    node.page.pageId shouldBe 1L
                }
                pageLock.close()
            }
        }
        `when`("delete page 0L"){
            then("should throw InvalidPageIdException"){
                shouldThrow<StorageManagerException.InvalidPageIdException> { storageManager.deletePage(0L) }
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
                    val newNode = Node.from(indexConfig, newPage, keySerializer)
                    newNode.page.type shouldBe PageType.LEAF_NODE
                }
                newPageLock.close()
            }
        }
    }
}){
    companion object {
        private val storageConfig = StorageConfig("./js-test-storage-manager.db")
        private val indexConfig = IndexConfig()
        private const val POOL_SIZE = 20
        val testSchema = KeySchema(
            listOf(
                Column("id", ColumnType.INT, descending = false),
                Column("name", ColumnType.STRING, descending = false),
                Column("birth", ColumnType.LOCAL_DATE, descending = false)
            )
        )
        private val keySerializer = MultiColumnKeySerializer(testSchema)
        private val diskManager = DiskManager(storageConfig, indexConfig)
        private val lruConfig = MidpointLruConfig(20)
        private val replacer = MidpointLRUPolicy(lruConfig)
        private val bufferPoolManager = BufferPoolManager(diskManager, replacer, indexConfig, POOL_SIZE)
        private val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
        private val storageManager = StorageManager(freeSpaceManager, bufferPoolManager, indexConfig)
        private val databaseInitializer = DatabaseInitializer(bufferPoolManager)
    }
}