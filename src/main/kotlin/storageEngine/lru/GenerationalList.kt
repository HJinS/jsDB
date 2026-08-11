package storageEngine.lru

import storageEngine.exception.LRUException


/**
 * InnoDB의 Midpoint Insertion LRU를 재현한 old/young 2-세그먼트 리스트.
 *
 * 물리적으로는 [DoublyLinkedList] 하나(**head** → young → [midPoint] → old → **tail**)이고,
 * `midPoint`는 old 영역에서 head(=young)와 가장 가까운 경계 노드를 가리킨다
 * (young 영역이 비어있으면 head 바로 다음 노드가 곧 경계).
 *
 * `size < lruOldMinLength`인 동안은 old/young 구분 자체가 없는 순수 LRU로 동작한다
 * (InnoDB의 `BUF_LRU_OLD_MIN_LEN` 미만 구간, `buf_LRU_add_block_low`/`buf_LRU_remove_block` 참고).
 * 이 클래스가 `lruOldMinLength`/`youngRatio`/`PromotionRule`을 전부 소유하므로,
 * 호출자([storageEngine.lru.FrameNodePolicy])는 "지금 순수 LRU인지, old/young이 나뉘어 있는지"를 몰라도 된다
 * - pin 생명주기·frameId 매핑만 책임지고 순서/승격 판단은 전부 이 클래스에 위임한다.
 *
 * @constructor
 * @param youngRatio old/young 분리 상태에서 young 영역이 차지할 목표 비율(0.0~1.0). [adjustRatio]가 이 값을
 *   현재 `size`에 곱해 매 호출마다 상한을 다시 계산한다.
 * @param capacity 버퍼 풀 전체 프레임 수. old/young 비율 계산에는 쓰이지 않고,
 *   [removeOldest]가 빈 리스트에서 evict를 시도했을 때 예외 메시지에만 쓰인다.
 * @param lruOldMinLength 순수 LRU ↔ old/young 분리 상태를 가르는 임계값(InnoDB `BUF_LRU_OLD_MIN_LEN`에 대응).
 *   `size`가 이 값에 도달하는 순간 MidpointLRU, 이 값 밑으로 떨어지는 순간 일단 LRU 로써 동작한다.
 * @param promotionRule old 노드가 재접근됐을 때 young으로 승격시킬지 판단하는 정책([touch]에서만 사용).
 * */
class GenerationalList(
    private val youngRatio: Double,
    private val capacity: Int,
    private val lruOldMinLength: Int,
    private val promotionRule: PromotionRule
) {
    private val linkedList = DoublyLinkedList()
    private var midPoint: LRUNode? = null

    var youngCount = 0
        private set

    var oldCount = 0
        private set

    var size = 0
        private set

    val oldest
        get() = linkedList.getLast()

    /**
     * 이미 리스트에 있는(pin되지 않은) 노드를 재접근했을 때 호출.
     *
     * `midPoint == null`(= 아직 순수 LRU 상태)이면 old/young 구분 자체가 없으므로
     * `node.isOld` 값과 무관하게 그냥 head로 옮긴다(순수 LRU의 "접근 시 최상단 이동").
     * `midPoint != null`(old/young이 나뉜 상태)일 때만 old 노드에 한해
     * [PromotionRule.isPromotable]로 old 영역 체류 시간을 확인 후 승격 여부를 결정한다.
     *
     * @param node 재접근된 노드. 이미 [linkedList]에 들어있는(=pin되지 않은) 상태여야 한다.
     * */
    fun touch(node: LRUNode) {
        if(midPoint != null && node.isOld){
            if(promotionRule.isPromotable(node)) promoteYoung(node)
        } else{
            touchYoung(node)
        }
    }

    /**
     * young 영역(head)에 새 노드를 삽입한다.
     *
     * 이 삽입으로 `size`가 `lruOldMinLength`에 막 도달하면(InnoDB의 `buf_LRU_old_init` 시점과 동일)
     * 지금까지 순수 LRU로 쌓여있던 노드 전체를 일괄 old로 전환한다.
     *
     * @param node 삽입할 노드. 아직 [linkedList]에 없는 상태여야 한다.
     * */
    fun addYoung(node: LRUNode){
        linkedList.addFirst(node)
        youngCount ++
        size ++
        node.isOld = false
        if(size == lruOldMinLength) markAllAsOld()
        adjustRatio()
    }

    /**
     * old 영역(midPoint 앞)에 새 노드를 삽입한다.
     *
     * `size < lruOldMinLength`면 old 영역이라는 개념 자체가 아직 없으므로,
     * 호출자가 old로 요청했더라도 무시하고 [addYoung]으로 위임한다
     * (InnoDB `buf_LRU_add_block_low`가 길이 미달 시 `old` 인자를 무시하고 head에 넣는 것과 동일).
     *
     * @param node 삽입할 노드. 아직 [linkedList]에 없는 상태여야 한다.
     * */
    fun addOld(node: LRUNode){
        if(size < lruOldMinLength){
            addYoung(node)
        } else{
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
    }

    /**
     * evict 대상(tail)을 꺼낸다. [convertToPlainLruIfNeeded]가 true를 반환하면(=이번 삭제로
     * `size`가 `lruOldMinLength` 밑으로 떨어져 이미 통째로 young 처리됨) `midPoint == node`
     * 보정은 필요 없다 - 그 결과가 곧바로 [markAllAsYoung]에 덮어써질 것이기 때문.
     *
     * @return 리스트에서 제거된, evict 대상 노드.
     * @throws LRUException.LRUEvictException 리스트가 비어 evict할 노드가 없을 때.
     * */
    fun removeOldest(): LRUNode {
        val node = linkedList.removeLast() ?: throw LRUException.LRUEvictException(capacity, youngCount, oldCount)
        if(node.isOld) oldCount-- else youngCount--
        size --
        if(!convertToPlainLruIfNeeded() && midPoint == node) midPoint = null
        return node
    }

    /**
     * pin 등으로 리스트에서 노드를 임의로 빼낼 때 사용. [removeOldest]와 마찬가지로
     * 제거 후 순수 LRU 전환 여부를 먼저 확인한다 - evict 경로가 아니라고
     * 임계값 재확인을 빠뜨리면, 여기서 크기가 줄어드는 케이스만 old 상태가 stale하게 남는다.
     * [convertToPlainLruIfNeeded]가 이미 전체를 young으로 되돌렸다면 [shrinkOldList]는
     * 어차피 덮어써질 결과라 건너뛴다.
     *
     * @param node [linkedList]에서 제거할 노드. 반드시 현재 리스트에 들어있는 노드여야 한다.
     * */
    fun remove(node: LRUNode){
        linkedList.remove(node)
        if(node.isOld) oldCount -- else youngCount --
        size --
        if(!convertToPlainLruIfNeeded() && midPoint == node) shrinkOldList()
    }

    /**
     * young 비율이 목표치를 넘으면 midPoint를 head 쪽으로 밀어 old 영역을 넓힌다.
     *
     * `maxYoungCount`는 `capacity`가 아니라 **현재 `size` 기준**으로 매번 새로 계산한다.
     * `capacity` 기준 고정값을 쓰면, 아직 버퍼 풀이 다 안 찼을 때(`size` ≪ `capacity`)
     * `youngCount`가 그 고정 상한을 절대 못 넘어서 이 루프가 사실상 죽은 코드가 되는 문제가 있었다.
     * */
    private fun adjustRatio(){
        val maxYoungCount = (size * youngRatio).toInt()
        var prevMidPoint = midPoint?.prev
        while(prevMidPoint != null && youngCount > maxYoungCount){
            prevMidPoint.isOld = true
            midPoint = prevMidPoint
            youngCount --
            oldCount ++
            prevMidPoint = midPoint?.prev
        }
    }

    /**
     * midPoint 노드 자신이 young으로 승격될 때, 경계를 그다음(old 쪽) 노드로 한 칸 물린다.
     *
     * old 영역에 이 노드 하나만 남아있던 경우 `next`가 실제 old 노드가 아니라 tail sentinel이 된다
     * - 이때는 old 영역이 완전히 빈 것이므로 sentinel을 경계로 남기지 말고 `null`로 되돌려야 한다.
     * (`promoteYoung`/`remove` 양쪽 호출 시점에 `oldCount`가 아직 안 줄었을 수도, 이미 줄었을 수도 있어
     * 카운트 대신 sentinel 여부를 직접 확인한다.)
     * */
    private fun shrinkOldList() {
        val next = midPoint?.next
        midPoint = if (next != null && !linkedList.isTail(next)) next else null
    }

    /**
     * 새로 삽입된 old 노드가 head와 가장 가까우므로, 그 노드가 곧 새 경계가 된다.
     *
     * @param node 방금 old 영역에 삽입된 노드. 이 노드가 새 [midPoint]가 된다.
     * */
    private fun expandOldList(node: LRUNode){
        midPoint = node
    }

    /**
     * 삭제로 인해 `size`가 `lruOldMinLength` 밑으로 떨어지면 순수 LRU로 역전환하고 `true`를 반환한다.
     * `midPoint != null` 가드는 "애초에 old/young이 나뉜 상태였을 때만" 역전환이 의미 있기 때문
     * — 이미 순수 LRU 상태(`midPoint == null`)에서 삭제가 일어난 경우는 할 일이 없다.
     *
     * 반환값은 호출부(`removeOldest`/`remove`)가 뒤이어 `midPoint`를 개별 보정할지 말지 결정하는 데 쓰인다
     * — 여기서 이미 전체를 young으로 되돌렸다면(`midPoint = null`까지 포함) 그 개별 보정은
     * 곧바로 덮어써질 낭비 작업이라 스킵해야 한다.
     *
     * @return 이번 호출에서 실제로 순수 LRU로 역전환했으면 `true`, 아직 old/young 분리를 유지해도 되면 `false`.
     * */
    private fun convertToPlainLruIfNeeded(): Boolean {
        if(midPoint != null && size < lruOldMinLength){
            markAllAsYoung()
            return true
        }
        return false
    }

    /**
     * 순수 LRU → old/young 분리로 전환하는 시점(size == lruOldMinLength)에 1회 호출.
     * 그때까지 쌓인 노드 전체를 old로 마킹하고, head 바로 다음 노드를 경계로 삼는다
     * (young 영역이 아직 비어있으니 경계가 head에 붙어 있는 게 맞음).
     * InnoDB의 `buf_LRU_old_init()`과 동일한 지점/동작이다.
     * */
    private fun markAllAsOld(){
        linkedList.forEach { it.isOld = true }
        oldCount = size
        youngCount = 0
        midPoint = linkedList.getFirst()
    }

    /**
     * old/young 분리 → 순수 LRU로 역전환. 전체를 young으로 되돌리고 경계를 없앤다.
     * InnoDB `buf_LRU_remove_block`이 삭제 후 길이가 `BUF_LRU_OLD_MIN_LEN` 밑으로 떨어지면
     * 전체 `old` 플래그를 지우고 `LRU_old`를 `nullptr`로 되돌리는 것과 동일한 동작.
     * */
    private fun markAllAsYoung(){
        linkedList.forEach { it.isOld = false }
        oldCount = 0
        youngCount = size
        midPoint = null
    }

    /**
     * old 노드를 young으로 승격. midPoint 자신이 승격 대상이면(=old 영역에 이 노드 하나만 남았던 경우)
     * remove/addFirst로 링크를 바꾸기 전에 먼저 경계를 옮겨야 한다 - 순서가 바뀌면
     * shrinkOldList()가 이미 young 리스트로 옮겨진(=이전 링크 정보를 잃은) 노드를 기준으로 동작해
     * 엉뚱한 노드를 새 경계로 잡는다.
     *
     * @param node 승격할 old 노드. 반드시 현재 old 영역에 들어있는 노드여야 한다.
     * */
    internal fun promoteYoung(node: LRUNode){
        if(midPoint == node) shrinkOldList()
        linkedList.remove(node)
        oldCount --
        linkedList.addFirst(node)
        youngCount ++
        node.isOld = false
        adjustRatio()
    }

    /**
     * node를 young list 가장 앞단으로 갱신.
     *
     * `touch()`가 normal LRU 상태(`midPoint == null`)로 판단해 이 경로로 보낸 경우,
     * 재접근한 노드가 (과거 split 상태에서 old로 남아있던) `isOld == true`일 수 있다.
     * 그 경우 `isOld`만 슬쩍 바꾸면 `oldCount`/`youngCount`가 어긋나므로,
     * 플래그와 카운트를 함께 정정한 뒤 head로 옮긴다.
     *
     * @param node head로 옮길 노드. 반드시 현재 [linkedList]에 들어있는 노드여야 한다.
     * */
    internal fun touchYoung(node: LRUNode){
        if(node.isOld){
            node.isOld = false
            oldCount --
            youngCount ++
        }
        linkedList.remove(node)
        linkedList.addFirst(node)
    }
}
