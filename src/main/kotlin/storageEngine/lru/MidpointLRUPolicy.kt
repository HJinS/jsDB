package storageEngine.lru

import config.MidpointLruConfig
import java.lang.System.currentTimeMillis


class MidpointLRUPolicy(
    midpointLruConfig: MidpointLruConfig
): ReplacementPolicy {
    private val map = HashMap<Int, LRUNode>()
    private val generationalList:  GenerationalList = GenerationalList(
        midpointLruConfig.youngRatio,
        midpointLruConfig.capacity
    )
    private val promotionRule: PromotionRule = PromotionRule(
        midpointLruConfig.lruOldBlocksTimeMs
    )

    override fun evict(): Int {
        val oldNode = generationalList.removeOldest()
        val frameId = oldNode.frameId
        map.remove(frameId)
        return frameId
    }

    /**
     * 다시 접근하는 경우만 사용
     * lastAccessTime 갱신
     * pin 상태인 경우 lru list 관련 스킵 -> return
     * frameId in map -> is old list -> get time gap -> promote to young
     * frameId in map -> is young list -> move to head
     * 기본적으로 frameId 가 map 에 있는지 확인 후 있는 경우 현재 접근 시간 및 lastAccessTIme 차이를 통해 young 으로 올릴지 말지 선택
     * 없는 경우 old 영역에 넣음
     *
     * young, old adjust 필요
     *
     * */
    override fun add(frameId: Int) {
        val now = currentTimeMillis()
        val node = map[frameId]!!
        node.lastAccessTime = now
        // 이미 pin 되어 있으면 접근 시각만 갱신하고 종료
        // pin 되어 있기 때문에 generationalList에 넣으면 안됨
        if(node.isPinned) return
        if(node.isOld){
            if(promotionRule.isPromotable(node)) generationalList.promoteYoung(node)
        } else generationalList.touchYoung(node)
    }

    override fun unpin(frameId: Int) {
        // 처음 접근할 때는 무조건 pin을 호출하는 것을 전제로 함
        val node = map[frameId]!!
        val now = currentTimeMillis()
        // map에 있다는 것은 이미 노드가 있다는 것임
        // node != null && !node.isPinned 인 케이스는 아무것도 하면 안됨
        if(node.isPinned){
            node.isPinned = false
            node.lastAccessTime = now
            // 원래 old 였는지 young 였는지를 가지고 각각 복귀
            if(node.isOld) generationalList.addOld(node) else generationalList.addYoung(node)
        }
    }

    override fun pin(frameId: Int) {
        val node = map[frameId]
        if(node == null){
            map[frameId] = LRUNode(frameId).apply { 
                isPinned = true
                isOld = true
            }
        } else if(!node.isPinned){
            generationalList.remove(node)
            node.resetLink()
            node.isPinned = true
        }
    }
}
