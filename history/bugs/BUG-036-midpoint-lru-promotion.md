# BUG-036 Midpoint LRU — old 노드가 승격되지 않음, midPoint 포인터 오류, 소규모 pool에서 비율 조정 불가

- **커밋:** `6ef41ac`
- **날짜:** 2026-08-11
- **컴포넌트:** `MidpointLRUPolicy.kt`(-> `FrameNodePolicy.kt`로 개명), `GenerationalList.kt`, `PromotionRule.kt`, `LRUNode.kt`
- **상태:** 수정 완료
- **이슈:** #29 / **PR:** #32

## 증상

- old 영역에 충분히 머문 프레임을 다시 접근해도 young으로 승격되지 않아, 자주 쓰는 프레임이 evict 대상이 되었다.
- 승격 직후 `midPoint`가 엉뚱한 노드를 가리켰다.
- pool이 작을 때는 old/young 비율 조정(`adjustRatio`)이 아예 일어나지 않았고, 전부 young인 상태에서 `removeOldest`가 young 노드를 꺼내며 `oldCount`를 깎을 수 있었다.

## 원인

**1. `lastAccessTime`을 승격 판단 전에 덮어씀**

```kotlin
// 수정 전
override fun add(frameId: Int) {
    val now = currentTimeMillis()
    val node = map[frameId]!!
    node.lastAccessTime = now          // 먼저 덮어씀
    if(node.isPinned) return
    if(node.isOld){
        if(promotionRule.isPromotable(node)) generationalList.promoteYoung(node)   // now - now ≈ 0 → 항상 false
    } else generationalList.touchYoung(node)
}
```

`isPromotable`은 `현재 시각 - lastAccessTime`이 임계값(`lruOldBlocksTimeMs`)을 넘었는지 보는데, 그 직전에 `lastAccessTime`을 현재 시각으로 바꿔서 승격이 절대 일어나지 않았다.

**2. `promoteYoung`의 순서 오류**

```kotlin
// 수정 전
fun promoteYoung(node: LRUNode){
    linkedList.remove(node)
    oldCount --
    linkedList.addFirst(node)         // 먼저 young 쪽으로 옮김
    youngCount ++
    node.isOld = false
    if(midPoint == node) shrinkOldList()   // 이미 링크를 잃은 노드 기준으로 경계를 재계산
    adjustRatio()
}
```

승격 대상이 `midPoint` 자신일 때, 리스트에서 옮긴 뒤 `shrinkOldList()`를 호출해 새 경계를 잘못 잡았다.

**3. 소규모 리스트 처리 부재**

- MySQL(InnoDB)은 리스트 길이가 `BUF_LRU_OLD_MIN_LEN` 미만이면 old/young을 나누지 않고 순수 LRU로 동작하는데, 이 로직이 없었다.
- `maxYoungCount`가 현재 크기가 아니라 전체 capacity 기준의 고정값이라, 노드가 적을 때는 `youngCount`가 그 값을 넘지 않아 `adjustRatio`가 발동하지 않았다.

## 수정

- 설정에 `lruOldMinLength` 추가. 리스트 크기가 이 값 미만이면 순수 LRU로 동작하고, 딱 도달하는 시점에 old/young 분리로 전환(`markAllAsOld`). 삭제로 다시 미만이 되면 역전환.
- `lastAccessTime`은 노드 생성 시 1회만 설정하고, 재접근 시점에는 갱신하지 않는다 (`PromotionRule`은 그 값 이후 경과 시간으로 판단).
- `promoteYoung`은 `midPoint == node`이면 리스트에서 옮기기 **전에** `shrinkOldList()`를 먼저 호출.
- 승격·경계·순수 LRU 판단 로직을 `MidpointLRUPolicy`에서 `GenerationalList`(`touch`, `addOld`, `addYoung` 등)로 옮기고, 정책 클래스는 `FrameNodePolicy`로 개명해 frameId <-> 노드 매핑과 pin 생명주기만 맡게 했다.
  - `PromotionRule`, `LRUNode` 등을 분리.
- 테스트 추가: `GenerationalListTest`, `PromotionRuleTest`, `DoublyLinkedListTest`, `FrameNodePolicyTest`.

## 관련

- BUG-017, BUG-023: 같은 buffer pool 계층의 동시성 수정
