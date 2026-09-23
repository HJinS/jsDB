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

    /**
     * @return The real data node right after head (the most recently touched one), or `null` if
     *   the list is empty.
     *
     * On an empty list, `head.next` points at the `tail` sentinel itself, so `count == 0` must be
     * checked first to avoid mistaking the sentinel for a real node and returning it.
     * */
    fun getFirst(): LRUNode? = if(count == 0) null else head.next

    /**
     * @return The real data node right before tail (the longest-untouched one), or `null` if the
     *   list is empty.
     *
     * On an empty list, `tail.prev` points at the `head` sentinel itself, so the same `count == 0`
     * guard as [getFirst] is needed.
     * */
    fun getLast(): LRUNode? = if(count == 0) null else tail.prev

    /**
     * Checks whether [node] is the tail sentinel rather than real data. Used to prevent a
     * boundary pointer from ending up pointing at the sentinel once the old region empties out.
     *
     * @param node The node to check.
     * @return `true` if it's the sentinel (`tail`) itself.
     * */
    internal fun isTail(node: LRUNode): Boolean = node === tail

    /**
     * Removes [node] from its current position. [node] must already be in the list (its
     * `prev`/`next` already set).
     *
     * @param node The node to remove.
     * */
    fun remove(node: LRUNode){
        val prevFrame = node.prev!!
        val nextFrame = node.next!!
        prevFrame.next = nextFrame
        nextFrame.prev = prevFrame
        count--
    }


    /** @return Removes and returns the node right before tail, or `null` if the list is empty. */
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
     *
     * @param node The new node to insert.
     * @param targetNode The already-listed reference node; `node` is inserted right before it
     *   (toward head).
     * */
    fun add(node: LRUNode, targetNode: LRUNode){
        val prevFrame = targetNode.prev!!
        prevFrame.next = node
        targetNode.prev = node
        node.next = targetNode
        node.prev = prevFrame
        count++
    }

    /**
     * Inserts [node] right after head (the front of the young region).
     *
     * @param node The node to insert. Must not already be in the list.
     * */
    fun addFirst(node: LRUNode){
        val insertPoint = head.next!!
        head.next = node
        insertPoint.prev = node
        node.next = insertPoint
        node.prev = head
        count++
    }

    /**
     * Inserts [node] right before tail (the oldest end of the list).
     *
     * @param node The node to insert. Must not already be in the list.
     * */
    fun addLast(node: LRUNode){
        val insertPoint = tail.prev!!
        tail.prev = node
        insertPoint.next = node
        node.next = tail
        node.prev = insertPoint
        count++
    }

    /**
     * Iterates head-to-tail over real data nodes only (sentinels excluded). Used for
     * [GenerationalList]'s batch old/young conversions.
     *
     * @param action The action to run for each node.
     * */
    internal fun forEach(action: (LRUNode) -> Unit){
        var nodePointer = head.next
        while(nodePointer != null && nodePointer.next != null){
            action(nodePointer)
            nodePointer = nodePointer.next
        }
    }

    /**
     * @param frameId The frame id to look for.
     * @return The matching [LRUNode], or `null` if none.
     * */
    internal fun findNode(frameId: Int): LRUNode?{
        var nodePointer = head.next
        while(nodePointer != null &&  nodePointer.next != null){
            if(nodePointer.frameId == frameId)  return nodePointer
            nodePointer = nodePointer.next
        }
        return null
    }

    /** @return The frame ids listed in head-to-tail order. */
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
