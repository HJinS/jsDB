package storage

import config.IndexConfig
import config.MidpointLruConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import storageEngine.BufferPoolManager
import storageEngine.DiskManager
import storageEngine.exception.BufferPoolManagerException
import storageEngine.exception.LRUException
import storageEngine.lru.MidpointLRUPolicy
import storageEngine.util.LockMode

class BufferPoolManagerTest: BehaviorSpec({
    given("an empty BufferPoolManager"){
        val replacer = MidpointLRUPolicy(MidpointLruConfig)
        val bufferPoolManager = BufferPoolManager(diskManager, replacer, IndexConfig, 1024)
        var pageId = 1L
        clearMocks(diskManager)
        val pageLock1 = bufferPoolManager.fetchPage(pageId, LockMode.READ)
        val frame1 = pageLock1.frame
        `when`("fetch page with ${LockMode.READ}"){
            then("the frame's pinCount should be 1"){
                frame1.pinCount.get() shouldBe 1
            }
            then("the frame's isDirty should be false"){
                frame1.isDirty.get() shouldBe false
            }
            then("diskManager.readPage should be called"){
                verify(exactly = 1) {diskManager.readPage(pageId, frame1.data)}
            }
            then("the frame should have read lock"){
                frame1.latch.isWriteLocked shouldBe false
            }
        }
        `when`("close page lock"){
            pageLock1.close()
            then("the frame's pinCount should be 0"){
                frame1.pinCount.get() shouldBe 0
            }
            then("the frame's isDirty should be false"){
                frame1.isDirty.get() shouldBe false
            }
        }
        clearMocks(diskManager)
        val pageLock2 = bufferPoolManager.fetchPage(2L, LockMode.WRITE)
        val frame2 = pageLock2.frame
        `when`("fetch page with ${LockMode.WRITE}"){
            then("the frame's pinCount should be 1"){
                frame2.pinCount.get() shouldBe 1
            }
            then("the frame's isDirty should be false"){
                frame2.isDirty.get() shouldBe false
            }
            then("diskManager.readPage should be called"){
                verify(exactly = 1) {diskManager.readPage(2L, frame2.data)}
            }
            then("the frame should have read lock"){
                frame2.latch.isWriteLocked shouldBe true
            }
        }
        `when`("delete page 1L"){
            bufferPoolManager.deletePage(1L)
            then("frame's pageId should be -1L"){
                frame1.pageId.get() shouldBe -1L
            }
            then("frame's isDirty should be false"){
                frame1.isDirty.get() shouldBe false
            }
        }
        `when`("delete page 2L"){
            then("PageInUseException should be thrown"){
                shouldThrow<BufferPoolManagerException.PageInUseException> { bufferPoolManager.deletePage(2L) }
            }
        }
        `when`("close page 2L"){
            then("PageInUseException should be thrown"){
                shouldThrow<BufferPoolManagerException.PageInUseException> { bufferPoolManager.deletePage(2L) }
            }
        }
        clearMocks(diskManager)
        val pageId3 = 3L
        val pageLock3 = bufferPoolManager.newPage(pageId3)
        val frame3 = pageLock3.frame
        `when`("new page 3"){
            then("the frame's pageId should be $pageId3"){
                frame3.pageId.get() shouldBe pageId3
            }
            then("the frame should be dirty"){
                frame3.isDirty.get() shouldBe true
            }
            then("the frame has write lock"){
                frame3.latch.isWriteLocked shouldBe true
            }
            then("the diskManager.writePage should not be called"){
                verify(exactly = 0) {diskManager.writePage(pageId3, frame3.data)}
                verify(exactly = 0) {diskManager.readPage(pageId3, frame3.data)}
            }
        }
        `when`("unpin page which doesn't exist"){
            then("should throw PageNotFoundInCacheException"){
                shouldThrow<BufferPoolManagerException.PageNotFoundInCacheException> { bufferPoolManager.unpinPage(4L, true) }
            }
        }
        `when`("flush page which doesn't exist"){
            then("should throw PageNotFoundInCacheException"){
                shouldThrow<BufferPoolManagerException.PageNotFoundInCacheException> { bufferPoolManager.flushPage(4L) }
            }
        }
        `when`("flush page 3"){
            pageLock3.setDirty()
            pageLock3.close()
            clearMocks(diskManager)
            bufferPoolManager.flushPage(pageId3)
            then("diskManager.writePage should be called"){
                verify(exactly = 1) { diskManager.writePage(pageId3, frame3.data) }
            }
            then("the frame should not be dirty"){
                frame3.isDirty.get() shouldBe false
            }
        }
    }

    given("a full BufferPoolManager"){
        val replacer = MidpointLRUPolicy(MidpointLruConfig)
        val bufferPoolManager = BufferPoolManager(diskManager, replacer, IndexConfig, 2)
        clearMocks(diskManager)
        val pageLock1 = bufferPoolManager.fetchPage(1L, LockMode.WRITE)
        bufferPoolManager.fetchPage(2L, LockMode.READ)
        pageLock1.setDirty()
        `when`("fetch page3 with ${LockMode.READ}"){
            then("LRUEvictException error should be thrown because all frame is pinned."){
                val error = shouldThrow<BufferPoolManagerException.UnExpectedException>{bufferPoolManager.fetchPage(3L, LockMode.READ)}
                error.cause shouldBe instanceOf(LRUException.LRUEvictException::class)
            }
        }
        `when`("close dirty page lock"){
            pageLock1.close()
            val frame1 = pageLock1.frame
            then("the frame's pinCount should be 0"){
                frame1.pinCount.get() shouldBe 0
            }
            then("the frame's isDirty should be true"){
                frame1.isDirty.get() shouldBe true
            }
        }
        `when`("fetchPage 1 again"){
            clearMocks(diskManager)
            val pageLock1 = bufferPoolManager.fetchPage(1L, LockMode.WRITE)
            val frame1 = pageLock1.frame
            then("the frame's pinCount should be 1"){
                frame1.pinCount.get() shouldBe 1
            }
            then("the frame's isDirty should be true(not flushed yet)"){
                frame1.isDirty.get() shouldBe true
            }
            then("no diskManager should be called"){
                verify(exactly = 0) {diskManager.readPage(1L, frame1.data)}
                verify(exactly = 0) {diskManager.writePage(1L, frame1.data)}
            }
            then("the frame should have write lock"){
                frame1.latch.isWriteLocked shouldBe true
            }
            pageLock1.close()
        }
        `when`("fetchPage 1 again with LockMode: ${LockMode.READ}"){
            clearMocks(diskManager)
            val pageLock1 = bufferPoolManager.fetchPage(1L, LockMode.READ)
            val frame1 = pageLock1.frame
            then("the frame's pinCount should be 1"){
                frame1.pinCount.get() shouldBe 1
            }
            then("the frame's isDirty should be true(not flushed yet)"){
                frame1.isDirty.get() shouldBe true
            }
            then("no diskManager should be called"){
                verify(exactly = 0) {diskManager.readPage(1L, frame1.data)}
                verify(exactly = 0) {diskManager.writePage(1L, frame1.data)}
            }
            then("the frame should have read lock"){
                frame1.latch.isWriteLocked shouldBe false
            }
            pageLock1.close()
        }

        clearMocks(diskManager)
        val pageLock3 = bufferPoolManager.fetchPage(3L, LockMode.WRITE)
        val frame3 = pageLock3.frame
        `when`("fetch page3"){
            then("the frame's pinCount should be 1"){
                frame3.pinCount.get() shouldBe 1
            }
            then("the frame's isDirty should be false"){
                frame3.isDirty.get() shouldBe false
            }
            then("diskManager.writePage should be called(page 1 should be evicted)"){
                verify(exactly = 1) {diskManager.writePage(1L, frame3.data)}
            }
            then("the frame should have read lock"){
                frame3.latch.isWriteLocked shouldBe true
            }
        }

        `when`("close dirty page 3 lock"){
            pageLock3.setDirty()
            pageLock3.close()
            val frame3 = pageLock3.frame
            then("the frame's pinCount should be 0"){
                frame3.pinCount.get() shouldBe 0
            }
            then("the frame's isDirty should be true"){
                frame3.isDirty.get() shouldBe true
            }
        }

        `when`("get new page 4"){
            clearMocks(diskManager)
            val pageId4 = 4L
            val pageLock4 = bufferPoolManager.newPage(pageId4)
            val frame4 = pageLock4.frame
            then("the frame's pinCount should be 1"){
                frame4.pinCount.get() shouldBe 1
            }
            then("the frame's isDirty should be true"){
                frame4.isDirty.get() shouldBe true
            }
            then("diskManager.writePage should be called(page 3 should be evicted)"){
                verify(exactly = 1) {diskManager.writePage(3L, frame4.data)}
            }
            then("the frame should have read lock"){
                frame4.latch.isWriteLocked shouldBe true
            }
        }
    }

}){
    companion object{
        val diskManager = mockk<DiskManager>(relaxed = true)
    }
}