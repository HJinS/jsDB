package storageEngine.lru


/**
 *
 * LinkedList 구조 관리
 * 새로운 노드가 어디에 있어야 할 지 및 비율 조정 등 수행
 * */
class GenerationalList(
    val youngRatio: Double,
    val capacity: Int
) {
    private val linkedList = DoublyLinkedList()
    private var midPoint: LRUNode? = null
    private val maxYoungCount by lazy { (capacity * youngRatio).toInt() }

    var youngCount = 0
        private set

    var oldCount = 0
        private set

    var size = 0
        private set

    val oldest
        get() = linkedList.getLast()

    fun promoteYoung(node: LRUNode){
        linkedList.remove(node)
        oldCount --
        linkedList.addFirst(node)
        youngCount ++
        node.isOld = false
        if(midPoint == node) shrinkOldList()
        adjustRatio()
    }

    /**
     * 이미 young인 node를 young list 가장 앞단으로 갱신
     * */
    fun touchYoung(node: LRUNode){
        require(!node.isOld){
            "Node should be young to touch"
        }
        linkedList.remove(node)
        linkedList.addFirst(node)
    }

    fun addYoung(node: LRUNode){
        linkedList.addFirst(node)
        youngCount ++
        size ++
        node.isOld = false
        adjustRatio()
    }

    fun addOld(node: LRUNode){
        val currentMidPoint = midPoint
        if(currentMidPoint == null){
            linkedList.addLast(node)
        }else {
            linkedList.add(node, currentMidPoint)
        }
        oldCount ++
        size ++
        expandOldList(node)
    }

    fun removeOldest(): LRUNode? {
        val node = linkedList.removeLast() ?: return null
        oldCount --
        size --
        if(midPoint == node) midPoint = null
        return node
    }

    fun remove(node: LRUNode){
        linkedList.remove(node)
        if(node.isOld) oldCount -- else youngCount --
        size --
        if(midPoint == node) shrinkOldList()
    }

    private fun adjustRatio(){
        var prevMidPoint = midPoint?.prev
        while(prevMidPoint != null && youngCount > maxYoungCount){
            prevMidPoint.isOld = true
            midPoint = prevMidPoint
            youngCount --
            oldCount ++
            prevMidPoint = midPoint?.prev
        }
    }

    private fun shrinkOldList() {
        val oldMidPoint = midPoint
        midPoint = oldMidPoint?.next ?: oldMidPoint
    }

    private fun expandOldList(node: LRUNode){
        midPoint = node
    }
}