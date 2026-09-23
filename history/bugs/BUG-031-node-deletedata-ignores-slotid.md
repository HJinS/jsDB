# BUG-031 Node.deleteData — slotId 인자를 무시하고 항상 0번 슬롯 삭제

- **커밋:** `719d3e6` (수정) / `804331a` (도입, 2026-03-10)
- **날짜:** 2026-05-04
- **컴포넌트:** `Node.kt` — `deleteData`
- **상태:** 수정 완료

## 증상

특정 슬롯을 지우려 해도 항상 0번 슬롯이 지워져 트리가 망가졌다.

## 원인

```kotlin
// 수정 전
fun deleteData(slotId: Int) = page.deleteData(0)   // slotId 무시
```

이 함수를 `slotId`가 0이 아닌 경로에서 호출하는 곳이 영향을 받았다.

- `LeafNode.redistribute`: 왼쪽 sibling에서 빌릴 때 `targetNode.deleteData(recordCount - 1)`이 마지막 키 대신 첫 키를 지움.
- `LeafNode.merge`: 부모의 separator를 `parentNode.deleteData(separationKey)`로 지울 때 잘못된 슬롯이 삭제됨.

## 수정

```kotlin
// 수정 후
fun deleteData(slotId: Int) = page.deleteData(slotId)
```

## 남은 사항

- `BTREE_IMPROVEMENT.md`는 `deleteAt`으로 이름을 통일하고 하나만 남기라고 제안했지만, 현재 `Node.kt`에는 `deleteData(slotId)`와 `deleteAt(slotId)`가 동일한 동작으로 둘 다 남아 있다.

## 관련

- BUG-005, BUG-006, BUG-007: redistribute/merge 방향과 separator 관련 다른 수정
- BUG-032: 같은 커밋의 `LeafNode.redistribute` separator 수정
