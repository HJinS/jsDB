package storageEngine.lru

import config.MidpointLruConfig


/**
 * [ReplacementPolicy] 어댑터. old/young 판단·승격·순수 LRU 전환 같은 알고리즘 내부는
 * 전부 [GenerationalList]에 위임하고, 여기선 두 가지만 책임진다.
 * - `frameId ↔ LRUNode` 매핑([map])
 * - pin 생명주기(처음 pin되면 노드 생성, pin 중엔 리스트에서 제외, unpin 시 원래 상태로 복귀)
 *
 * 그래서 [add]가 한 줄짜리 위임으로 보이는 건 의도된 결과다 - "지금 순수 LRU인지,
 * old 노드가 승격 가능한지"를 판단하는 로직이 전부 [GenerationalList] 한 곳에만
 * 존재하게 만든 것이지, 이 클래스의 역할이 사라진 게 아니다.
 *
 * @constructor
 * @param midpointLruConfig [GenerationalList]에 그대로 전달되는 `youngRatio`/`capacity`/`lruOldMinLength`와,
 *   [PromotionRule]을 만드는 데 쓰이는 `lruOldBlocksTimeMs`를 담고 있는 설정값.
 * */
class FrameNodePolicy(
    midpointLruConfig: MidpointLruConfig
): ReplacementPolicy {
    private val map = HashMap<Int, LRUNode>()
    private val generationalList:  GenerationalList = GenerationalList(
        midpointLruConfig.youngRatio,
        midpointLruConfig.capacity,
        midpointLruConfig.lruOldMinLength,
        PromotionRule(
            midpointLruConfig.lruOldBlocksTimeMs
        )
    )

    /** @return evict된 프레임의 frameId. */
    override fun evict(): Int {
        val oldNode = generationalList.removeOldest()
        val frameId = oldNode.frameId
        map.remove(frameId)
        return frameId
    }

    /**
     * pin되지 않은(=리스트에 있는) 노드의 재접근. old/young·순수 LRU 판단은 [GenerationalList.touch]가 전담.
     *
     * @param frameId 재접근된 프레임의 id. [map]에 이미 등록되어 있어야 한다.
     * */
    override fun add(frameId: Int) {
        val node = map[frameId]!!
        if(!node.isPinned) generationalList.touch(node)
    }

    /**
     * pin 해제 시 리스트로 복귀. `node.isOld`는 이 노드가 pin되기 전 원래 있던 위치를 그대로 보존한 값이라
     * [GenerationalList.addOld]/[GenerationalList.addYoung] 중 그 값에 맞는 쪽을 그대로 호출하면 된다
     * - 순수 LRU 상태인지 여부는 `addOld` 내부에서 알아서 걸러주므로 여기서 별도 분기가 필요 없다.
     *
     * @param frameId unpin 할 프레임의 id.
     * */
    override fun unpin(frameId: Int) {
        val node = map[frameId]!!
        if(node.isPinned){
            node.isPinned = false
            if(node.isOld) generationalList.addOld(node) else generationalList.addYoung(node)
        }
    }

    /**
     * 처음 보는 frameId면 새 [LRUNode]를 만들어 pin 상태로 등록만 해두고(아직 [GenerationalList]엔 안 들어감),
     * 이미 있던(리스트에 들어있던) 노드를 다시 pin하는 거면 리스트에서 빼내고 링크를 초기화한다.
     * `isOld = true`로 초기화해두는 값은 잠정치일 뿐이고, 최종적으로는 [unpin]에서
     * [GenerationalList.addOld]가 순수 LRU 여부에 따라 다시 판단해 덮어쓴다.
     *
     * @param frameId pin할 프레임의 id.
     * */
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
