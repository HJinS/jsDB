package storage

import config.SimpleConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import storageEngine.BufferPoolManager
import storageEngine.MetaPageManager
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.lru.FrameNodePolicy

class FreeSpaceManagerTest: BehaviorSpec({
    given("an empty free space manager"){
        val replacer = FrameNodePolicy(config.storageConfig.midPointLruConfig)
        val bufferPoolManager = BufferPoolManager(diskManager, replacer, config.indexConfig, 100)
        val metaPageManager = MetaPageManager(bufferPoolManager)
        val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
        val dummyPageIdsCreated = mutableListOf<Long>()
        metaPageManager.initMetaPage()
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
        val config = SimpleConfig()
        val diskManager = mockk<DiskManager>(relaxed = true)
    }
}