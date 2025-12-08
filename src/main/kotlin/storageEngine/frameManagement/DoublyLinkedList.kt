package storageEngine.frameManagement


/**
 * ```
 *       ---------       ---------       ---------       ---------       ---------       ---------
 * ------|  head  |------|        |------|        |------|  mid   |------|        |------|  tail  |------
 *       ---------       ---------       ---------       ---------       ---------       ---------
 * ```
 * */
internal class DoublyLinkedList {
    private val head = Node(-1)
    private val tail = Node(-1)
    private var count: Int = 0
    val size: Int
        get() = count

    init {
        head.next = tail
        tail.prev = head
    }

    fun remove(node: Node){
        val prevNode = node.prev!!
        val nextNode = node.next!!
        prevNode.next = nextNode
        nextNode.prev = prevNode
        count -= 1
    }


    fun removeLast(): Node{
        val lastNode = tail.prev!!
        val newLastNode = lastNode.next!!
        newLastNode.next = tail
        tail.prev = newLastNode
        return lastNode
    }

    /**
     * add [node] to the left of [targetNode]
     * */
    fun add(node: Node, targetNode: Node){
        val prevNode = targetNode.prev!!
        prevNode.next = node
        targetNode.prev = node
        node.next = targetNode
        node.prev = prevNode
        count += 1
    }

    fun addFirst(node: Node){
        val insertPoint = head.next!!
        head.next = node
        insertPoint.prev = node
        node.next = insertPoint
        node.prev = head
        count += 1
    }

    fun addLast(node: Node){
        val insertPoint = tail.prev!!
        tail.prev = node
        insertPoint.next = node
        node.next = tail
        node.prev = insertPoint
        count += 1
    }
}