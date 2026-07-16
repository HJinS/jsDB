package storage.lru

import config.MidpointLruConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import storageEngine.exception.LRUException
import storageEngine.lru.MidpointLRUPolicy

class MidpointLRUPolicyTest: BehaviorSpec({
    given("a midpoint lruPolicy with 10 frames"){
        val midPointLruConfig = MidpointLruConfig(30)
        val midPointLruPolicy = MidpointLRUPolicy(midPointLruConfig)
        val dummyFrameIds = (0..300).shuffled().iterator()
        val framesPinned = mutableListOf<Int>()
        val framesUnpinned = mutableListOf<Int>()
        val frameAdded = mutableListOf<Int>()
        repeat(10){
            val frameId = dummyFrameIds.next()
            midPointLruPolicy.pin(frameId)
            framesPinned.add(frameId)
        }
        `when`("evict one time"){
            then("LRUEvictException error should be thrown"){
                val error = shouldThrow<LRUException.LRUEvictException> { midPointLruPolicy.evict() }
                error shouldHaveMessage "Could not evict frame. May be all frame is pinned or buffer pool is empty. young: 0, old: 0, capacity: 30"
            }
        }
        `when`("unpin first 5 frames and evict one time"){
            for(idx in 0..4){
                val frameId = framesPinned.removeAt(idx)
                framesUnpinned.add(frameId)
                midPointLruPolicy.unpin(frameId)
            }
            then("evicted frameId should be ${framesUnpinned[0]}"){
                val frameId = framesUnpinned.removeAt(0)
                midPointLruPolicy.evict() shouldBe frameId
            }
        }
        `when`("pin 4 unpinned frames again"){
            for(frameId in framesUnpinned){
                midPointLruPolicy.pin(frameId)
                framesPinned.addFirst(frameId)
            }
            framesUnpinned.clear()
            `when`("unpin all frames and add all frames"){
                for(frameId in framesPinned){
                    midPointLruPolicy.unpin(frameId)
                    framesUnpinned.addFirst(frameId)
                }
                framesPinned.clear()
                for(frameId in framesUnpinned){
                    midPointLruPolicy.add(frameId)
                    frameAdded.add(frameId)
                }
                framesUnpinned.clear()

                `when`("evict one frame"){
                    val evicted = midPointLruPolicy.evict()
                    val expectedFrame = frameAdded.removeLast()
                    then("evicted frameId should be $expectedFrame"){
                        evicted shouldBe expectedFrame
                    }
                }
            }
        }
        `when`("pin all frames again and unpin and evict one time"){
            for(frameId in frameAdded){
                midPointLruPolicy.pin(frameId)
                midPointLruPolicy.unpin(frameId)
            }
            val expectedFrame = frameAdded.removeFirst()
            val evicted = midPointLruPolicy.evict()
            then("evicted frameId should be $expectedFrame"){
                evicted shouldBe expectedFrame
            }
        }
    }
})
