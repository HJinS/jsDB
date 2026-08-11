package storage.lru

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import storageEngine.lru.GenerationalList
import storageEngine.lru.LRUNode
import storageEngine.lru.PromotionRule


/*
* 가장 왼쪽이 가장 어린것을 기준으로 함
*
* 앞의 두 given은 lruOldMinLength=0으로 둬서 "항상 old/young이 분리된 상태"를 전제로 하는
* 기존 테스트 시나리오를 그대로 유지한다. 순수 LRU ↔ old/young 분리 전환 자체(markAllAsOld/
* markAllAsYoung이 실제로 트리거되는 경계)는 맨 아래 별도 given 블록에서 다룬다.
* */
class GenerationalListTest: BehaviorSpec({
    given("an empty generational list") {

        val youngRatio = 0.3
        val generationalList = GenerationalList(
            youngRatio, 300, lruOldMinLength = 0, promotionRule = PromotionRule(0L)
        )
        val dummyFrameIds = (0..300).shuffled().iterator()
        val oldNodeList = ArrayDeque<LRUNode>()
        val youngNodeList = ArrayDeque<LRUNode>()
        var currentNodeCount = 40

        `when`("add $currentNodeCount items to old list"){
            repeat(currentNodeCount){
                val frameId = dummyFrameIds.next()
                val node = LRUNode(frameId=frameId)
                generationalList.addOld(node)
                oldNodeList.addFirst(node)
            }
            then("old count should be size"){
                generationalList.oldCount shouldBe generationalList.size
            }

            then("oldest node should be first item in nodeList"){
                generationalList.oldest shouldBe oldNodeList.last()
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("remove one old node"){
            val node = oldNodeList.removeLast()
            generationalList.remove(node)
            then("oldCount should be ${currentNodeCount - 1}"){
                generationalList.oldCount shouldBe currentNodeCount - 1
            }
            currentNodeCount --
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("remove oldest node"){
            val node = oldNodeList.removeLast()
            val nodeRemoved = generationalList.removeOldest()
            then("oldCount should be ${currentNodeCount - 1}"){
                generationalList.oldCount shouldBe currentNodeCount - 1
                node shouldBe nodeRemoved
            }
            currentNodeCount --
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("touchYoung is called on an old node that is not the current midPoint"){
            // addOld(tempNode)는 tempNode를 잠시 midPoint로 만드는데, touchYoung은 touch()의
            // "순수 LRU 상태(midPoint == null)에서 재접근한 노드가 stale old로 남아있는" 케이스를
            // 시뮬레이션하는 것이라 실제로 이 조합(터치 대상 == 현재 midPoint)은 touch()를 통해서는
            // 절대 발생하지 않는다. guardNode를 하나 더 old로 넣어 tempNode가 midPoint에서 밀려나게 한 뒤 검증한다.
            val tempNode = LRUNode(frameId = dummyFrameIds.next())
            generationalList.addOld(tempNode)
            val guardNode = LRUNode(frameId = dummyFrameIds.next())
            generationalList.addOld(guardNode)

            then("isOld/oldCount/youngCount should self-correct instead of throwing"){
                val oldCountBefore = generationalList.oldCount
                val youngCountBefore = generationalList.youngCount

                generationalList.touchYoung(tempNode)

                tempNode.isOld shouldBe false
                generationalList.oldCount shouldBe oldCountBefore - 1
                generationalList.youngCount shouldBe youngCountBefore + 1
            }

            // 뒤이은 단계들의 oldNodeList/youngNodeList 북키핑에 영향을 주지 않도록 원상 복구.
            generationalList.remove(tempNode)
            generationalList.remove(guardNode)
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("promote oldest node"){
            val node = oldNodeList.removeLast()
            youngNodeList.addFirst(node)
            generationalList.promoteYoung(node)
            then("size should be $currentNodeCount"){
                generationalList.size shouldBe currentNodeCount
            }
            then("oldCount + youngCount should be size"){
                generationalList.oldCount + generationalList.youngCount shouldBe generationalList.size
            }
            then("youngCount should be 1"){
                generationalList.youngCount shouldBe 1
            }
            then("oldCount should be ${currentNodeCount - generationalList.youngCount}"){
                generationalList.oldCount shouldBe currentNodeCount - generationalList.youngCount
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("promoting every remaining old node one by one"){
            // promoteYoung()은 매번 adjustRatio()를 다시 돌리므로, "old를 전부 young으로 승격시키면
            // youngCount == 전체 개수"라는 가정 자체가 더 이상 성립하지 않는다 — 승격이 계속될수록
            // 비율(youngRatio) 초과분이 그때그때 다시 old로 밀려나기 때문이다(issue #29 ②의 수정 대상이었던 동작).
            // 그래서 매 승격 이후 "youngCount가 그 시점 size 기준 비율 상한을 넘지 않는다"만 불변식으로 검증한다.
            var promotionCount = 0
            while(oldNodeList.isNotEmpty()){
                val node = oldNodeList.removeLast()
                generationalList.promoteYoung(node)
                promotionCount++

                then("(promotion #$promotionCount) youngCount should never exceed floor(size * youngRatio)"){
                    val expectedMaxYoungCount = (generationalList.size * youngRatio).toInt()
                    generationalList.youngCount shouldBe minOf(generationalList.youngCount, expectedMaxYoungCount)
                }
            }
            then("size should still be $currentNodeCount, split across old/young"){
                generationalList.size shouldBe currentNodeCount
                generationalList.oldCount + generationalList.youngCount shouldBe currentNodeCount
            }
        }
        // 승격 도중 비율 강제로 임의의 노드가 다시 old로 밀려날 수 있어 oldNodeList/youngNodeList
        // 북키핑이 더 이상 실제 상태와 일치한다고 보장할 수 없다 — 이후로는 개별 노드 추적 대신
        // removeOldest()로 리스트를 통째로 비우면서 size/카운트 정합성만 검증한다.

        `when`("draining the whole list via removeOldest"){
            then("size should reach 0 after removing every node exactly once"){
                var removedCount = 0
                while(generationalList.size > 0){
                    generationalList.removeOldest()
                    removedCount++
                }
                removedCount shouldBe currentNodeCount
                generationalList.oldCount shouldBe 0
                generationalList.youngCount shouldBe 0
            }
        }
    }
    given("a generational list with 40 old items") {
        val youngRatio = 0.3
        val capacity = 150
        val generationalList = GenerationalList(
            youngRatio, capacity, lruOldMinLength = 0, promotionRule = PromotionRule(0L)
        )
        val dummyFrameIds = (0..300).shuffled().iterator()
        val oldNodeList = ArrayDeque<LRUNode>()
        var currentNodeCount = 40
        repeat(currentNodeCount){
            val frameId = dummyFrameIds.next()
            val node = LRUNode(frameId=frameId)
            generationalList.addOld(node)
            oldNodeList.addFirst(node)
        }
        checkNodeOrder(oldNodeList, ArrayDeque())

        // youngRatio는 capacity가 아니라 그 순간의 size 기준으로 매번 다시 계산된다
        // (issue #29 — capacity 기준 고정값을 쓰면 size가 작을 때 비율 조정이 사실상 죽은 코드가 되던 버그).
        // 그래서 기대값도 매번 그 시점의 size로 다시 계산해서 비교한다.
        `when`("add 45 young items one by one"){
            repeat(45){ iteration ->
                val frameId = dummyFrameIds.next()
                val node = LRUNode(frameId=frameId)
                generationalList.addYoung(node)
                currentNodeCount++

                then("(insertion #$iteration) youngCount should never exceed floor(size * youngRatio)"){
                    val expectedMaxYoungCount = (generationalList.size * youngRatio).toInt()
                    generationalList.youngCount shouldBe minOf(generationalList.youngCount, expectedMaxYoungCount)
                }
            }
            then("size should be $currentNodeCount"){
                generationalList.size shouldBe currentNodeCount
            }
            then("oldCount + youngCount should still be size"){
                generationalList.oldCount + generationalList.youngCount shouldBe generationalList.size
            }
        }

        `when`("add 1 more young item"){
            val node = LRUNode(dummyFrameIds.next())
            generationalList.addYoung(node)
            currentNodeCount++

            then("youngCount should still respect the (freshly recomputed) size-relative ratio"){
                val expectedMaxYoungCount = (generationalList.size * youngRatio).toInt()
                generationalList.youngCount shouldBe minOf(generationalList.youngCount, expectedMaxYoungCount)
            }
        }

        `when`("touch an already-young node"){
            val node = LRUNode(dummyFrameIds.next())
            generationalList.addYoung(node)

            generationalList.touchYoung(node)
            then("touched node should stay young and move back to the front"){
                node.isOld shouldBe false
                node.prev.shouldNotBeNull()
                node.prev!!.frameId shouldBe -1
            }
        }
    }

    given("a generational list with lruOldMinLength=5 (plain LRU <-> split transition)"){
        val youngRatio = 0.5
        val lruOldMinLength = 5
        var now = 0L
        val generationalList = GenerationalList(
            youngRatio, 100, lruOldMinLength = lruOldMinLength,
            promotionRule = PromotionRule(lruOldBlocksTimeMs = 100L, clock = { now })
        )
        val dummyFrameIds = (0..300).shuffled().iterator()
        val nodes = mutableListOf<LRUNode>()

        `when`("adding nodes while size stays below lruOldMinLength"){
            repeat(lruOldMinLength - 1){
                val node = LRUNode(dummyFrameIds.next(), lastAccessTime = now)
                generationalList.addOld(node)
                nodes.add(node)
            }
            then("addOld should be redirected to young — nothing becomes old yet"){
                generationalList.size shouldBe lruOldMinLength - 1
                generationalList.oldCount shouldBe 0
                generationalList.youngCount shouldBe lruOldMinLength - 1
                nodes.all { !it.isOld } shouldBe true
            }
        }

        `when`("size reaches lruOldMinLength exactly"){
            val triggerNode = LRUNode(dummyFrameIds.next(), lastAccessTime = now)
            generationalList.addYoung(triggerNode)
            nodes.add(triggerNode)
            then("every resident node should be bulk-converted to old (markAllAsOld)"){
                generationalList.size shouldBe lruOldMinLength
                generationalList.oldCount shouldBe lruOldMinLength
                generationalList.youngCount shouldBe 0
                nodes.all { it.isOld } shouldBe true
            }
        }

        `when`("touching an old node before lruOldBlocksTimeMs has elapsed"){
            val target = nodes.first()
            now += 50 // < lruOldBlocksTimeMs(100)
            generationalList.touch(target)
            then("it should stay old — not promotable yet"){
                target.isOld shouldBe true
                generationalList.oldCount shouldBe lruOldMinLength
                generationalList.youngCount shouldBe 0
            }
        }

        `when`("touching the same old node after lruOldBlocksTimeMs has elapsed"){
            val target = nodes.first()
            now += 100 // total elapsed since lastAccessTime(0) now exceeds 100
            generationalList.touch(target)
            then("it should be promoted to young"){
                target.isOld shouldBe false
                generationalList.oldCount shouldBe lruOldMinLength - 1
                generationalList.youngCount shouldBe 1
            }
        }

        `when`("removing a node drops size back below lruOldMinLength"){
            generationalList.removeOldest()
            then("every remaining node should revert to young (markAllAsYoung)"){
                generationalList.size shouldBe lruOldMinLength - 1
                generationalList.oldCount shouldBe 0
                generationalList.youngCount shouldBe generationalList.size
            }
        }
    }
}){
    companion object{
        fun checkNodeOrder(oldList: List<LRUNode>, youngList: List<LRUNode>){
            for((idx, node) in oldList.withIndex()){
                if(idx == oldList.lastIndex) break
                node.next shouldBe oldList[idx+1]
            }

            for(idx in oldList.indices.reversed()){
                if(idx == 0) break
                val node = oldList[idx]
                node.prev shouldBe oldList[idx-1]
            }

            for((idx, node) in youngList.withIndex()){
                if(idx == youngList.lastIndex) break
                node.next shouldBe youngList[idx+1]
            }

            for(idx in youngList.indices.reversed()){
                if(idx == 0) break
                val node = youngList[idx]
                node.prev shouldBe youngList[idx-1]
            }
        }
    }
}
