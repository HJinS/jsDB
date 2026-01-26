package storageEngine.lru

import config.MidpointLruConfig
import storageEngine.exception.MidPointLRUException
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
        midpointLruConfig.capacity,
        midpointLruConfig.lruOldBlocksTimeMs
    )

    override fun evict(): Int {
        val oldNode = generationalList.removeOldest() ?: throw MidPointLRUException.LRUListFullException(
            generationalList.capacity,
            generationalList.youngCount,
            generationalList.oldCount,
            null
        )
        val frameId = oldNode.frameId
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
     * 기본적으로 frameId 가 map 에 있는지 확인 후 있는 경우 현재 접근 시간 및 lastAccessTIme 차이를 통해 young 으로 올릴지 말지 선택
     * 없는 경우 old 영역에 넣음
     *
     * young, old adjust 필요
     *
     * */
    override fun add(frameId: Int) {
        val now = currentTimeMillis()
        val node = map[frameId]
        if(node == null){
            promotionRule.checkSize(map.size)
            val newFrame = LRUNode(frameId, lastAccessTime = now)
            generationalList.addOld(newFrame)
            map[frameId] = newFrame
        }else{
            if(node.isOld){
                if(promotionRule.isPromotable(node)) generationalList.promoteYoung(node)
            } else generationalList.touchYoung(node)
            node.lastAccessTime = now
        }
    }

    override fun unpin(frameId: Int) {
        val node = map[frameId]
        val now = currentTimeMillis()
        if(node != null){
            generationalList.addYoung(node)
            node.lastAccessTime = now
        } else{
            promotionRule.checkSize(map.size)
            val lruNode = LRUNode(frameId, lastAccessTime = now)
            generationalList.addOld(lruNode)
            map[frameId] = lruNode
        }
    }

    override fun pin(frameId: Int) {
        val node = map[frameId]
        if(node != null && !(node.prev == null && node.next == null)){
            generationalList.remove(node)
            node.resetLink()
        } else {
            map[frameId] = LRUNode(frameId)
        }
    }
}