package storageEngine.frameManagement

import config.MidpointLruConfig
import storageEngine.exception.MidPointLRUException
import java.lang.System.currentTimeMillis


class MidpointLRUPolicy(
    midpointLruConfig: MidpointLruConfig
): ReplacementPolicy {
    private val map = HashMap<Int, Frame>()
    private val generationalList:  GenerationalList = GenerationalList(midpointLruConfig.youngRatio, midpointLruConfig.capacity)
    private val promotionRule: PromotionRule = PromotionRule(
        midpointLruConfig.capacity,
        midpointLruConfig.lruOldBlocksTimeMs
    )

    override fun evict(): Int? {
        val oldFrame = generationalList.removeOldest() ?: return null
        val frameId = oldFrame.frameId
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
    override fun access(frameId: Int) {
        val now = currentTimeMillis()
        val frame = map[frameId]
        if(frame == null){
            promotionRule.checkSize(map.size)
            val newFrame = Frame(frameId, lastAccessTime = now)
            generationalList.addOld(newFrame)
            map[frameId] = newFrame
        }else{
            if(!frame.isPinned){
                if(frame.isOld){
                    if(promotionRule.isPromotable(frame)) generationalList.promoteYoung(frame)
                } else generationalList.touchYoung(frame)
            }
            frame.lastAccessTime = now
        }
    }

    override fun pin(frameId: Int) {
        val node = map[frameId] ?: throw MidPointLRUException.IllegalPinStateException(frameId, null)
        generationalList.pinNode(node)
    }

    override fun unpin(frameId: Int) {
        val node = map[frameId] ?: throw MidPointLRUException.IllegalPinStateException(frameId, null)
        generationalList.unPinNode(node)
    }
}