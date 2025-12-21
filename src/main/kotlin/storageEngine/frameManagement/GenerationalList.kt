package storageEngine.frameManagement


/**
 *
 * LinkedList 구조 관리
 * 새로운 노드가 어디에 있어야 할 지 및 비율 조정 등 수행
 * */
class GenerationalList(
    val youngRatio: Double
) {
    private val linkedList = DoublyLinkedList()
    private var midPoint: Node? = null

    var youngCount = 0
        private set

    var oldCount = 0
        private set

    fun promoteYoung(node: Node){
        linkedList.remove(node)
        oldCount --
        linkedList.addFirst(node)
        youngCount ++
        node.isOld = false
        if(midPoint == node) shrinkOldList()
    }

    fun touchYoung(node: Node){
        linkedList.remove(node)
        linkedList.addFirst(node)
    }

    fun addOld(node: Node){
        val currentMidPoint = midPoint
        if(currentMidPoint == null){
            linkedList.addLast(node)
        }else {
            linkedList.add(node, currentMidPoint)
        }
        oldCount ++
        expandOldList(node)
    }

    fun removeOldest(): Node? {
        val node = linkedList.removeLast() ?: return null
        oldCount --
        if(midPoint == node) midPoint = null
        return node
    }

    fun pinNode(node: Node){
        if(!node.isPinned) {
            linkedList.remove(node)
            node.isPinned = true
            if(node.isOld) oldCount -- else youngCount --
            if(midPoint == node) shrinkOldList()
        }
    }

    fun unPinNode(node: Node){
        if(node.isPinned) {
            linkedList.addFirst(node)
            node.isPinned = false
            if(node.isOld) oldCount ++ else youngCount ++
        }
    }

    fun adjustRatio(){

    }

    fun getOldest() = linkedList.getLast()

    private fun shrinkOldList() {
        val oldMidPoint = midPoint
        midPoint = oldMidPoint?.next ?: oldMidPoint
    }

    private fun expandOldList(node: Node){
        midPoint = node
    }
}