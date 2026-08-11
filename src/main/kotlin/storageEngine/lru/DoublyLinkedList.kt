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
     * @return head 바로 다음(가장 최근에 touch된) 실제 데이터 노드. 리스트가 비어있으면 `null`.
     *
     * 빈 리스트에서는 `head.next`가 `tail` sentinel 자신을 가리키므로, `count == 0`을 먼저 확인해야
     * "sentinel을 실제 노드로 착각해서 반환"하는 걸 막을 수 있다.
     * */
    fun getFirst(): LRUNode? = if(count == 0) null else head.next

    /**
     * @return tail 바로 앞(가장 오래 방치된) 실제 데이터 노드. 리스트가 비어있으면 `null`.
     *
     * 빈 리스트에서는 `tail.prev`가 `head` sentinel 자신을 가리키므로, [getFirst]와 같은 이유로
     * `count == 0` 가드가 필요하다.
     * */
    fun getLast(): LRUNode? = if(count == 0) null else tail.prev

    /**
     * [node]가 실존 데이터가 아니라 tail sentinel인지 확인. old 영역이 비어질 때 경계 포인터가 sentinel을 가리키는 걸 막는 데 사용.
     *
     * @param node 확인할 노드.
     * @return sentinel(`tail`) 자신이면 t`rue`.
     * */
    internal fun isTail(node: LRUNode): Boolean = node === tail

    /**
     * [node]를 현재 위치에서 제거한다. [node]가 이미 리스트에 들어있어(`prev`/`next`가 세팅되어) 있어야 한다.
     *
     * @param node 제거할 노드.
     * */
    fun remove(node: LRUNode){
        val prevFrame = node.prev!!
        val nextFrame = node.next!!
        prevFrame.next = nextFrame
        nextFrame.prev = prevFrame
        count--
    }


    /** @return tail 바로 앞 노드를 제거하고 반환. 리스트가 비어있으면 `null`. */
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
     * @param node 새로 삽입할 노드.
     * @param targetNode 삽입 기준이 되는, 이미 리스트에 들어있는 노드. `node`는 이 노드 바로 앞(head 쪽)에 들어간다.
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
     * [node]를 head 바로 다음(young 영역의 맨 앞)에 삽입한다.
     *
     * @param node 삽입할 노드. 아직 리스트에 없어야 한다.
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
     * [node]를 tail 바로 앞(리스트의 가장 오래된 끝)에 삽입한다.
     *
     * @param node 삽입할 노드. 아직 리스트에 없어야 한다.
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
     * head→tail 순서로 실제 데이터 노드(sentinel 제외)만 순회. [GenerationalList]의 일괄 old/young 전환에 사용.
     *
     * @param action 각 노드마다 실행할 동작.
     * */
    internal fun forEach(action: (LRUNode) -> Unit){
        var nodePointer = head.next
        while(nodePointer != null && nodePointer.next != null){
            action(nodePointer)
            nodePointer = nodePointer.next
        }
    }

    /**
     * @param frameId 찾을 프레임 id.
     * @return 일치하는 [LRUNode], 없으면 `null`.
     * */
    internal fun findNode(frameId: Int): LRUNode?{
        var nodePointer = head.next
        while(nodePointer != null &&  nodePointer.next != null){
            if(nodePointer.frameId == frameId)  return nodePointer
            nodePointer = nodePointer.next
        }
        return null
    }

    /** @return head→tail 순서로 나열한 frameId 목록. */
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
