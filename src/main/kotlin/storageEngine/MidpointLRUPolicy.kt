package storageEngine

class MidpointLRUPolicy(private val capacity: Int): ReplacementPolicy{
    internal class Node(val key: Int, var prev: Node? = null, var next: Node? = null)
    private val map = HashMap<Int, Node>()
    private val head = Node(-1)
    private val tail = Node(-1)
    private var midPoint = tail

    init {
        head.next = tail
        tail.prev = head
    }

    override fun evict(): Int? {
        TODO("Not yet implemented")
    }

    /**
     * key 가 있으면 해당 키를 가장 앞으로
     * key가 없으면 midpoint에 key 삽입
     * */
    override fun access(key: Int) {
        if(key in map) {
            val node = map[key]!!
            if(node == midPoint){
                midPoint = node.next!!
            }
            remove(node)
            addToNew(node)
        // 없는 경우는 새로운 노드를 만들어서 midpoint 에 삽입
        }else{
            if(map.size >= capacity){
                // do evict
            }
            // 남은 공간 확인 후 자리가 없으면 evict 필요
            val newNode = Node(key)
            addToMidpoint(newNode)
            map[key] = newNode
        }
    }

    override fun pin() {

        TODO("Not yet implemented")
    }

    override fun unpin() {
        TODO("Not yet implemented")
    }

    private fun remove(node: Node){
        val prevNode = node.prev!!
        val nextNode = node.next!!
        prevNode.next = node.next
        nextNode.prev = prevNode
        midPoint = if(midPoint == node) nextNode else midPoint
    }

    private fun addToNew(node: Node){
        val preFirstNode = head.next!!
        node.prev = head
        node.next = preFirstNode
        head.next = node
        preFirstNode.prev = node
    }

    private fun addToMidpoint(node: Node){
        val newListTail = midPoint.prev!!
        newListTail.next = node
        midPoint.prev = node
        node.next = midPoint
        midPoint = node
    }
}