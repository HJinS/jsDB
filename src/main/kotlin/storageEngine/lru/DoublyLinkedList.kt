package storageEngine.lru


/**
 * ```
 *       ---------       ---------       ---------       ---------       ---------       ---------
 * ------|  head  |------|        |------|        |------|  mid   |------|        |------|  tail  |------
 *       ---------       ---------       ---------       ---------       ---------       ---------
 * ```
 * */
internal class DoublyLinkedList {
    private val head = LRUNode(-1)
    private val tail = LRUNode(-1)
    private var count: Int = 0
    val size: Int
        get() = count

    init {
        head.next = tail
        tail.prev = head
    }

    fun getLast(): LRUNode? = tail.prev

    fun remove(node: LRUNode){
        val prevFrame = node.prev!!
        val nextFrame = node.next!!
        prevFrame.next = nextFrame
        nextFrame.prev = prevFrame
        count--
    }


    fun removeLast(): LRUNode?{
        if(count == 0) return null
        val lastFrame = tail.prev!!
        val newLastFrame = lastFrame.prev!!
        newLastFrame.next = tail
        tail.prev = newLastFrame
        count--
        return lastFrame
    }

    /**
     * add [node] to the left of [targetNode]
     * */
    fun add(node: LRUNode, targetNode: LRUNode){
        val prevFrame = targetNode.prev!!
        prevFrame.next = node
        targetNode.prev = node
        node.next = targetNode
        node.prev = prevFrame
        count++
    }

    fun addFirst(node: LRUNode){
        val insertPoint = head.next!!
        head.next = node
        insertPoint.prev = node
        node.next = insertPoint
        node.prev = head
        count++
    }

    fun addLast(node: LRUNode){
        val insertPoint = tail.prev!!
        tail.prev = node
        insertPoint.next = node
        node.next = tail
        node.prev = insertPoint
        count++
    }

    internal fun findNode(frameId: Int): LRUNode?{
        var nodePointer = head.next
        while(nodePointer != null &&  nodePointer.next != null){
            if(nodePointer.frameId == frameId)  return nodePointer
            nodePointer = nodePointer.next
        }
        return null
    }

    internal fun traverseIds(): List<Int>{
        val result = mutableListOf<Int>()
        var nodePointer = head.next
        while(nodePointer != null && nodePointer.next != null){
            result.addLast(nodePointer.frameId)
            nodePointer = nodePointer.next
        }
        return result
    }
}