package storage.lru

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import storageEngine.lru.DoublyLinkedList
import storageEngine.lru.LRUNode

class DoublyLinkedListTest: BehaviorSpec({
    given("an empty double linked list"){
        val doublyLinkedList = DoublyLinkedList()
        val dummyFrameIds = (0..300).shuffled().iterator()
        val frameIdList = mutableListOf<Int>()
        `when`("insert dummy frames"){
            repeat(100){
                val frameId = dummyFrameIds.next()
                doublyLinkedList.addLast(LRUNode(frameId=frameId))
                frameIdList.addLast(frameId)
            }
            then("list size should be 100"){
                doublyLinkedList.size shouldBe 100
            }
        }
        `when`("delete random items"){
            val randomInt = Arb.int(min=0, max=99).next()
            val removedFrameId = frameIdList.removeAt(randomInt)
            then("traverse result ids should be frameIdList"){
                val removedNode = doublyLinkedList.findNode(removedFrameId)
                removedNode.shouldNotBeNull()
                doublyLinkedList.remove(removedNode)
                val traverseResult = doublyLinkedList.traverseIds()
                traverseResult shouldBe frameIdList
            }
            then("list size should be 99"){
                doublyLinkedList.size shouldBe 99
            }
        }

        `when`("remove last item"){
            then("the result should be same"){
                val lastItemF = frameIdList.removeLast()
                val lastItemD = doublyLinkedList.removeLast()
                lastItemD.shouldNotBeNull()
                lastItemF shouldBe lastItemD.frameId
            }
        }

        `when`("add item to the first"){
            then("the traverse result should be same with frameIdList"){
                val randomFrameId = dummyFrameIds.next()
                frameIdList.addFirst(randomFrameId)
                doublyLinkedList.addFirst(LRUNode(randomFrameId))
                val traverseResult = doublyLinkedList.traverseIds()
                traverseResult shouldBe frameIdList
            }
        }

        `when`("add item to random position"){
            val randomInt = Arb.int(min=0, max=99).next()
            val randomFrameId = dummyFrameIds.next()
            frameIdList.add(randomInt, randomFrameId)
            then("traverse result ids should be frameIdList"){
                val addTarget = doublyLinkedList.findNode(frameIdList[randomInt+1])
                addTarget.shouldNotBeNull()
                doublyLinkedList.add(LRUNode(randomFrameId), addTarget)
                val traverseResult = doublyLinkedList.traverseIds()
                traverseResult shouldBe frameIdList
            }
            then("list size should be 99"){
                doublyLinkedList.size shouldBe 100
            }
        }

        `when`("getFirst is called"){
            then("it should return the same node addFirst just inserted"){
                val node = LRUNode(dummyFrameIds.next())
                doublyLinkedList.addFirst(node)
                doublyLinkedList.getFirst() shouldBe node
            }
        }

        `when`("forEach is called"){
            then("it should visit every real data node in head-to-tail order, excluding sentinels"){
                val visited = mutableListOf<Int>()
                doublyLinkedList.forEach { visited.add(it.frameId) }
                visited shouldBe doublyLinkedList.traverseIds()
                visited.contains(-1) shouldBe false
            }
        }
    }

    given("isTail"){
        `when`("checking a real data node"){
            val doublyLinkedList = DoublyLinkedList()
            val node = LRUNode(1)
            doublyLinkedList.addFirst(node)
            then("it should not be reported as the tail sentinel"){
                doublyLinkedList.isTail(node) shouldBe false
            }
        }

        `when`("removeLast reaches the last real node, leaving the list empty"){
            val doublyLinkedList = DoublyLinkedList()
            val node = LRUNode(2)
            doublyLinkedList.addFirst(node)
            val removed = doublyLinkedList.removeLast()
            then("getLast should now return null, with no dangling reference to the removed node"){
                removed shouldBe node
                doublyLinkedList.getLast() shouldBe null
                doublyLinkedList.getFirst() shouldBe null
            }
        }
    }
})