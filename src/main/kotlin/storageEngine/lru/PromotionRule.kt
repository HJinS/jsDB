package storageEngine.lru

import java.lang.System.currentTimeMillis

/**
 * old 노드가 재접근됐을 때 young으로 승격시킬지 판단하는 정책. InnoDB의 `innodb_old_blocks_time`에 대응하며,
 * [storageEngine.lru.GenerationalList]가 관리하는 `lruOldMinLength`(구조적 임계값)와는 별개의,
 * 시간 기반 임계값을 담당한다.
 *
 * @constructor
 * @param lruOldBlocksTimeMs old 영역에 머문 뒤 이 시간(ms)이 지나야 재접근 시 승격 대상이 된다.
 * @param clock 현재 시각을 얻는 함수. 기본값은 [System.currentTimeMillis]이며, 테스트에서 시간을 고정하려고 주입한다.
 * */
class PromotionRule(
    private val lruOldBlocksTimeMs: Long,
    private val clock: () -> Long = ::currentTimeMillis
) {
    /**
     * @param node 승격 여부를 판단할 노드.
     * @return `node.lastAccessTime`(생성 시 1회 설정된 값) 이후 [lruOldBlocksTimeMs]가 지났으면 `true`.
     * */
    fun isPromotable(node: LRUNode): Boolean {
        return clock() - node.lastAccessTime > lruOldBlocksTimeMs
    }
}