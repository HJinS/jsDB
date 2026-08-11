package storage.lru

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import storageEngine.lru.LRUNode
import storageEngine.lru.PromotionRule

class PromotionRuleTest: BehaviorSpec({
    given("a promotion rule with lruOldBlocksTimeMs=1000"){
        val lruOldBlocksTimeMs = 1000L
        var now = 0L
        val promotionRule = PromotionRule(lruOldBlocksTimeMs, clock = { now })

        `when`("elapsed time is exactly lruOldBlocksTimeMs"){
            val node = LRUNode(frameId = 1, lastAccessTime = 0L)
            now = lruOldBlocksTimeMs
            then("isPromotable should be false (strictly greater-than, not equal)"){
                promotionRule.isPromotable(node) shouldBe false
            }
        }

        `when`("elapsed time is one ms less than lruOldBlocksTimeMs"){
            val node = LRUNode(frameId = 2, lastAccessTime = 0L)
            now = lruOldBlocksTimeMs - 1
            then("isPromotable should be false"){
                promotionRule.isPromotable(node) shouldBe false
            }
        }

        `when`("elapsed time is one ms more than lruOldBlocksTimeMs"){
            val node = LRUNode(frameId = 3, lastAccessTime = 0L)
            now = lruOldBlocksTimeMs + 1
            then("isPromotable should be true"){
                promotionRule.isPromotable(node) shouldBe true
            }
        }

        `when`("node was just created (lastAccessTime == now)"){
            val node = LRUNode(frameId = 4, lastAccessTime = 5_000L)
            now = 5_000L
            then("isPromotable should be false"){
                promotionRule.isPromotable(node) shouldBe false
            }
        }
    }
})
