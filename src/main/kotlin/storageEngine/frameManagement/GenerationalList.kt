package storageEngine.frameManagement


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
    private var midPoint: Frame? = null
    private val maxYoungCount by lazy { (capacity * youngRatio).toInt() }

    var youngCount = 0
        private set

    var oldCount = 0
        private set

    fun promoteYoung(frame: Frame){
        linkedList.remove(frame)
        oldCount --
        linkedList.addFirst(frame)
        youngCount ++
        frame.isOld = false
        if(midPoint == frame) shrinkOldList()
        adjustRatio()
    }

    fun touchYoung(frame: Frame){
        linkedList.remove(frame)
        linkedList.addFirst(frame)
    }

    fun addOld(frame: Frame){
        val currentMidPoint = midPoint
        if(currentMidPoint == null){
            linkedList.addLast(frame)
        }else {
            linkedList.add(frame, currentMidPoint)
        }
        oldCount ++
        expandOldList(frame)
    }

    fun removeOldest(): Frame? {
        val frame = linkedList.removeLast() ?: return null
        oldCount --
        if(midPoint == frame) midPoint = null
        return frame
    }

    fun pinNode(frame: Frame){
        if(!frame.isPinned) {
            linkedList.remove(frame)
            frame.isPinned = true
            if(frame.isOld) oldCount -- else youngCount --
            if(midPoint == frame) shrinkOldList()
        }
    }

    fun unPinNode(frame: Frame){
        if(frame.isPinned) {
            linkedList.addFirst(frame)
            frame.isPinned = false
            if(frame.isOld) oldCount ++ else youngCount ++
        }
    }

    fun adjustRatio(){
        if (youngCount > maxYoungCount){
            val nextMidPoint = midPoint?.next
            if(nextMidPoint != null){
                nextMidPoint.isOld = true
                midPoint = nextMidPoint
                youngCount --
                oldCount ++
            }
        }
    }

    fun getOldest() = linkedList.getLast()

    private fun shrinkOldList() {
        val oldMidPoint = midPoint
        midPoint = oldMidPoint?.next ?: oldMidPoint
    }

    private fun expandOldList(frame: Frame){
        midPoint = frame
    }
}