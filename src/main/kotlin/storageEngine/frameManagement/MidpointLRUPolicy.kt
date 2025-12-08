package storageEngine.frameManagement

import storageEngine.exception.LRUException


class MidpointLRUPolicy(private val capacity: Int): ReplacementPolicy {
    private val map = HashMap<Int, Node>()
    private val linkedList = DoublyLinkedList()
    private var midPoint: Node? = null

    override fun evict(): Int {
        val node: Node = linkedList.removeLast()
        val key = node.key
        map.remove(key)
        return key
    }

    /**
     * key 가 있으면 해당 키를 가장 앞으로
     * key가 없으면 midpoint에 key 삽입
     * */
    override fun access(key: Int) {
        if(key in map) {
            val node = map[key]!!
            linkedList.remove(node)
            linkedList.addFirst(node)
            midPoint = if(midPoint == node) node.next else midPoint
        // 없는 경우는 새로운 노드를 만들어서 midpoint 에 삽입
        }else{
            if(map.size >= capacity){
                throw LRUException("No more space. Please call evict()", null)
            }
            // 남은 공간 확인 후 자리가 없으면 evict 필요
            val newNode = Node(key)
            midPoint?.let {
                linkedList.add(newNode, midPoint!!)
            } ?: run {
                linkedList.addLast(newNode)
            }
            midPoint = newNode
            map[key] = newNode
        }
    }

    override fun pin(key: Int) {
        val node = map[key] ?: return
        if(!node.isPinned){
            linkedList.remove(node)
            node.isPinned = true
        }
    }

    override fun unpin(key: Int) {
        val node = map[key] ?: return
        if(node.isPinned){
            linkedList.addFirst(node)
            node.isPinned = false
        }
    }

}