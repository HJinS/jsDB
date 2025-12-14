package storageEngine.frameManagement

import storageEngine.exception.MidPointLRUException
import java.lang.System.currentTimeMillis


class MidpointLRUPolicy(
    private val capacity: Int,
    private val lruOldBlocksTimeMs: Long = 1000
): ReplacementPolicy {
    private val map = HashMap<Int, Node>()
    private val linkedList = DoublyLinkedList()
    private var midPoint: Node? = null

    override fun evict(): Int {
        val node: Node = linkedList.removeLast()
        val frameId = node.frameId
        map.remove(frameId)
        return frameId
    }

    /**
     * key 가 있으면 해당 키를 가장 앞으로
     * key가 없으면 midpoint에 key 삽입

     * lastAccessTime 갱신
     * pin 상태인 경우 lru list 관련 스킵 -> return
     * frameId in map -> is old list -> get time gap -> promote to young
     * frameId in map -> is young list -> move to head
     * 기본적으로 frameId 가 map 에 있는지 확인 후 있는 경우 현재 접근 시간 및 lasteAccessTIme 차이를 통해 young 으로 올릴지 말지 선택
     * 없는 경우 old 영역에 넣음
     *
     * young, old adjust 필요
     *
     * */
    override fun access(frameId: Int) {
        val now = currentTimeMillis()
        val node = map[frameId]
        if(node != null){
            if(node.isPinned) {
                node.lastAccessTime = now
                return
            }
            if(node.isOld){
                val timeGap = now - node.lastAccessTime
                if (timeGap > lruOldBlocksTimeMs) {
                    // promote to young
                    linkedList.remove(node)
                    linkedList.addFirst(node)
                    node.isOld = false
                    midPoint = if(midPoint == node) node.next else midPoint
                }
            } else{
                // move to first(already in young list)
                linkedList.remove(node)
                linkedList.addFirst(node)
            }
            node.lastAccessTime = now
        } else{
            if(map.size >= capacity){
                throw MidPointLRUException.BufferPoolExhaustedException(capacity, null)
            }
            // 남은 공간 확인 후 자리가 없으면 evict 필요
            val newNode = Node(frameId, lastAccessTime = now)
            midPoint?.let {
                linkedList.add(newNode, it)
            } ?: run {
                linkedList.addLast(newNode)
            }
            midPoint = newNode
            map[frameId] = newNode
        }
    }

    override fun pin(frameId: Int) {
        val node = map[frameId] ?: throw MidPointLRUException.IllegalPinStateException(frameId, null)
        if(!node.isPinned){
            linkedList.remove(node)
            node.isPinned = true
        }
    }

    override fun unpin(frameId: Int) {
        val node = map[frameId] ?: throw MidPointLRUException.IllegalPinStateException(frameId, null)
        if(node.isPinned){
            linkedList.addFirst(node)
            node.isPinned = false
        }
    }

}