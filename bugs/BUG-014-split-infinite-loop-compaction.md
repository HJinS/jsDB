# BUG-014 Split 후 Compaction 미수행으로 인한 무한 Split

- **커밋:** `188e071`
- **날짜:** 2026-06-24
- **컴포넌트:** `LeafNode.kt`, `InternalNode.kt`
- **상태:** 수정 완료

## 증상

3000개 삽입 테스트에서 간헐적으로 동일 노드에서 split이 무한 반복됨. `recordCount <= 2` 상태에서 splitKeys가 비어있어 예외 발생.

## 원인

`SlottedPage.deleteData`는 레코드를 논리적으로 삭제하지만 `freeSpaceEnd` 포인터를 갱신하지 않음 (dead record 방식). `splitData`에서 bulk delete 후 실제 free space는 늘었지만 `freeSpaceEnd`가 갱신되지 않아 `wouldOverflow`가 여전히 true를 반환.

흐름:
1. leaf node overflow → split 시도
2. `splitData`에서 절반 삭제 → 실제 free space 증가, 그러나 `freeSpaceEnd` 미갱신
3. split 완료 후 새 키 삽입 → `wouldOverflow` 다시 true (freeSpaceEnd 부정확)
4. 같은 노드에서 다시 split → `recordCount <= 2` 상태에서 promotionKeyIdx가 잘못 계산 → splitKeys 빔

## 수정

`LeafNode.splitData` 및 `InternalNode.splitData` 끝에 `page.compaction()` 추가. compaction이 `freeSpaceEnd`를 올바르게 재계산하므로 이후 `wouldOverflow`가 정확한 freeSpace를 참조.
