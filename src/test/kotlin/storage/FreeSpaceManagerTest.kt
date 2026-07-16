package storage

import config.IndexConfig
import config.MidpointLruConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import storageEngine.BufferPoolManager
import storageEngine.DatabaseInitializer
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.lru.MidpointLRUPolicy

class FreeSpaceManagerTest: BehaviorSpec({
    given("an empty free space manager"){
        val replacer = MidpointLRUPolicy(midpointLruConfig)
        val bufferPoolManager = BufferPoolManager(diskManager, replacer, indexConfig, 100)
        val databaseInitializer = DatabaseInitializer(bufferPoolManager)
        val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
        val dummyPageIdsCreated = mutableListOf<Long>()
        databaseInitializer.initMetaPage()
         repeat(10){
             dummyPageIdsCreated.addLast(freeSpaceManager.getFreePageID())
        }
        for(pageId in dummyPageIdsCreated){
            val dummyPageLock = bufferPoolManager.newPage(pageId)
            dummyPageLock.close()
        }
        `when`("get free pageId with empty manager"){
            val freeSpaceId = freeSpaceManager.getFreePageID()
            then("freeSpaceId should be 11L(There is no page)"){
                freeSpaceId shouldBe 11L
            }
        }
        `when`("add all pages to the free list"){
            for(pageId in dummyPageIdsCreated){
                bufferPoolManager.deletePage(pageId)
                freeSpaceManager.addFreePageID(pageId)
            }

            then("when getFreePageId 10 times, then it should be 10L to 1L"){
                for(idx in 10L downTo 1L){
                    freeSpaceManager.getFreePageID() shouldBe idx
                }
            }
        }
        `when`("get free pageId again"){
            val freeSpaceId = freeSpaceManager.getFreePageID()
            then("freeSpaceId should be 0L(There is no page)"){
                freeSpaceId shouldBe 12L
            }
        }
    }
}){
    companion object{
        val midpointLruConfig = MidpointLruConfig()
        val indexConfig = IndexConfig()
        val diskManager = mockk<DiskManager>(relaxed = true)
    }
}