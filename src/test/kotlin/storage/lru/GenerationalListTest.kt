package storage.lru

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import storageEngine.lru.GenerationalList
import storageEngine.lru.LRUNode


/*
* 가장 왼쪽이 가장 어린것을 기준으로 함
* */
class GenerationalListTest: BehaviorSpec({
    given("an empty generational list") {

        val youngRatio = 0.3
        val generationalList = GenerationalList(
            youngRatio, 300
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

        `when`("touch oldest node"){
            val node = oldNodeList.last()
            then("IllegalArgumentException should be thrown"){
                val error = shouldThrow<IllegalArgumentException> { generationalList.touchYoung(node) }
                error.message shouldBe "Node should be young to touch"
            }
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

        `when`("promote $currentNodeCount items to young"){
            while(oldNodeList.isNotEmpty()){
                val node = oldNodeList.removeLast()
                generationalList.promoteYoung(node)
                youngNodeList.addFirst(node)
            }
            then("YoungCount should be $currentNodeCount"){
                generationalList.youngCount shouldBe currentNodeCount
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("remove all old node"){
            then("oldCount should be shrunk each iteration"){
                while(oldNodeList.isNotEmpty()){
                    val node = oldNodeList.removeLast()
                    generationalList.remove(node)
                    currentNodeCount --
                    generationalList.oldCount shouldBe currentNodeCount - generationalList.youngCount
                    generationalList.size shouldBe currentNodeCount
                }
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("remove all young node"){
            then("youngCount should be shrunk each iteration"){
                while(youngNodeList.isNotEmpty()){
                    val node = youngNodeList.removeLast()
                    generationalList.remove(node)
                    currentNodeCount--
                    generationalList.size shouldBe currentNodeCount
                    generationalList.youngCount shouldBe currentNodeCount
                }
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)
    }
    given("a generational list with 40 old items") {
        val youngRatio = 0.3
        val capacity = 150
        val generationalList = GenerationalList(
            youngRatio, capacity
        )
        val dummyFrameIds = (0..300).shuffled().iterator()
        val oldNodeList = ArrayDeque<LRUNode>()
        val youngNodeList = ArrayDeque<LRUNode>()
        var currentNodeCount = 40
        val maxYoungCount = (capacity * youngRatio).toInt()
        repeat(currentNodeCount){
            val frameId = dummyFrameIds.next()
            val node = LRUNode(frameId=frameId)
            generationalList.addOld(node)
            oldNodeList.addFirst(node)
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("add 45 young items"){
            repeat(45){
                val frameId = dummyFrameIds.next()
                val node = LRUNode(frameId=frameId)
                generationalList.addYoung(node)
                youngNodeList.addFirst(node)
                currentNodeCount++
            }
            then("youngCount should be ${youngNodeList.size}"){
                generationalList.youngCount shouldBe youngNodeList.size
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("add 1 new young item"){
            val node = LRUNode(dummyFrameIds.next())
            youngNodeList.addFirst(node)
            val lastYoungNode = youngNodeList.removeLast()
            generationalList.addYoung(node)
            then("youngCount should be $maxYoungCount"){
                generationalList.youngCount shouldBe maxYoungCount
            }
            then("the last young node should be changed to old node"){
                lastYoungNode.isOld shouldBe true
                lastYoungNode.next shouldBe oldNodeList.first()
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)

        `when`("touch young list's oldest node"){
            val oldestAmongYoung = youngNodeList.removeLast()
            youngNodeList.addFirst(oldestAmongYoung)

            generationalList.touchYoung(oldestAmongYoung)
            then("touched node should be the first node"){
                oldestAmongYoung.isOld shouldBe false
                oldestAmongYoung.prev.shouldNotBeNull()
                oldestAmongYoung.prev!!.frameId shouldBe -1
                oldestAmongYoung.next shouldBe youngNodeList[1]
            }
        }
        checkNodeOrder(oldNodeList, youngNodeList)
    }
}){
    companion object{
        fun checkNodeOrder(oldList: List<LRUNode>, youngList: List<LRUNode>){
            for((idx, node) in oldList.withIndex()){
                if(idx == oldList.lastIndex) break
                if(node.next != oldList[idx+1]){
                    println("node $idx ${node.next}")
                }
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