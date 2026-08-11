package storageEngine.lru

import java.lang.System.currentTimeMillis

/**
 * [GenerationalList]가 관리하는 이중연결리스트의 노드 하나. 버퍼 풀 프레임 하나에 대응한다.
 *
 * @constructor
 * @param frameId 이 노드가 대응하는 버퍼 풀 프레임의 id. 노드 생성 후 불변.
 * @param lastAccessTime 노드 생성 시 1회만 설정하고, 이후 재접근([GenerationalList.touch] 등)에서
 *   절대 갱신하지 않는다. old 영역에 "얼마나 오래 머물렀는지"를 재는 기준값이라, 매번 갱신해버리면
 *   재접근하는 순간 항상 `now - lastAccessTime ≈ 0`이 되어 승격 판정([PromotionRule.isPromotable])이
 *   영원히 실패한다 — 실제로 겪었던 버그. 프레임이 evict된 뒤 다른 페이지로 재사용될 때만
 *   (=새 [LRUNode] 인스턴스가 생성될 때만) 자연스럽게 새 값으로 갱신된다.
 * */
class LRUNode(
    val frameId: Int,
    var lastAccessTime: Long = currentTimeMillis()
) {
    /** [DoublyLinkedList] 상에서 head 쪽으로의 링크. */
    var next: LRUNode? = null

    /** [DoublyLinkedList] 상에서 tail 쪽으로의 링크. */
    var prev: LRUNode? = null

    /** old/young 여부. [GenerationalList]가 삽입·승격·전환 시점마다 일관되게 갱신해야 하는 값. */
    var isOld: Boolean = true

    /** pin 중이면 [GenerationalList]의 리스트에서 제외되어 evict 대상에서 빠진다. */
    var isPinned: Boolean = false

    /** pin될 때 이전 위치의 링크 정보를 지운다 — 다시 리스트에 들어갈 때 stale 포인터를 참조하지 않도록. */
    fun resetLink(){
        next = null
        prev = null
    }
}
