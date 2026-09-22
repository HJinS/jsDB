# BUG-033 InternalNode.redistribute — 삭제/삽입 뒤 수동 shiftSlot 중복 호출

- **커밋:** `719d3e6`
- **날짜:** 2026-05-04
- **컴포넌트:** `InternalNode.kt` — `redistribute`
- **상태:** 수정 완료

## 증상

internal node에서 sibling의 키를 빌리는 재분배(redistribute) 후 슬롯 배열이 손상될 위험이 있었다.

## 원인

`deleteData`/`insertData`가 이미 슬롯을 당기거나 미는 API가 된 뒤(BUG-028)에도, `redistribute`가 별도로 `shiftSlot`을 한 번 더 호출하고 있었다.

```kotlin
// 수정 전
// 오른쪽 sibling에서 빌림
val (siblingKey, siblingValue) = targetNode.page.deleteData(0)     // 이미 뒤 슬롯을 당김
...
targetNode.page.shiftSlot(0, targetNode.page.recordCount, -1)      // 또 한 번 당김

// 왼쪽 sibling에서 빌림
page.shiftSlot(0, page.recordCount, 1)                              // 미리 한 칸 밀고
...
page.insertData(0, removedParentKey, leftMostChild)                 // insertData가 또 밂
```

특히 `shiftSlot(0, n, -1)`은 코드상 계산으로 0번 슬롯을 `HEADER_SIZE - SLOT_SIZE` 위치, 즉 헤더 영역 끝으로 옮기는 호출이 된다.

## 수정

두 곳의 수동 `shiftSlot` 호출을 삭제했다. 슬롯 이동은 `deleteData`/`insertData`가 전담한다.

## 관련

- BUG-028: `deleteData`/`insertSlot`이 슬롯 이동을 책임지게 된 변경
- BUG-006, BUG-007, BUG-032: 다른 redistribute 수정
