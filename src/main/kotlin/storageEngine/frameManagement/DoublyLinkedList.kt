package storageEngine.frameManagement


/**
 * ```
 *       ---------       ---------       ---------       ---------       ---------       ---------
 * ------|  head  |------|        |------|        |------|  mid   |------|        |------|  tail  |------
 *       ---------       ---------       ---------       ---------       ---------       ---------
 * ```
 * */
internal class DoublyLinkedList {
    private val head = Frame(-1)
    private val tail = Frame(-1)
    private var count: Int = 0
    val size: Int
        get() = count

    init {
        head.next = tail
        tail.prev = head
    }

    fun getLast(): Frame? = tail.prev

    fun remove(frame: Frame){
        val prevFrame = frame.prev!!
        val nextFrame = frame.next!!
        prevFrame.next = nextFrame
        nextFrame.prev = prevFrame
        count--
    }


    fun removeLast(): Frame?{
        if(count == 0) return null
        val lastFrame = tail.prev!!
        val newLastFrame = lastFrame.prev!!
        newLastFrame.next = tail
        tail.prev = newLastFrame
        count--
        return lastFrame
    }

    /**
     * add [frame] to the left of [targetFrame]
     * */
    fun add(frame: Frame, targetFrame: Frame){
        val prevFrame = targetFrame.prev!!
        prevFrame.next = frame
        targetFrame.prev = frame
        frame.next = targetFrame
        frame.prev = prevFrame
        count++
    }

    fun addFirst(frame: Frame){
        val insertPoint = head.next!!
        head.next = frame
        insertPoint.prev = frame
        frame.next = insertPoint
        frame.prev = head
        count++
    }

    fun addLast(frame: Frame){
        val insertPoint = tail.prev!!
        tail.prev = frame
        insertPoint.next = frame
        frame.next = tail
        frame.prev = insertPoint
        count++
    }
}