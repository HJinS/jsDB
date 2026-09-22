# BUG-030 LeafNode.split — promotion key가 leaf에서 삭제되어 데이터 유실

- **커밋:** `719d3e6`
- **날짜:** 2026-05-04
- **컴포넌트:** `LeafNode.kt` — `split`
- **상태:** 수정 완료

## 증상

leaf split 후 부모에 올라간 separator key의 실제 데이터가 어느 leaf에도 남지 않아, 해당 키를 search해도 찾지 못했다.
B+Tree에서는 모든 실제 데이터가 leaf에 있어야 한다는 규칙 위반이었다.

## 원인

page 기반 재작성(`804331a`, 2026-03-10) 이후의 `split()`은 `splitData`로 오른쪽으로 보낼 레코드를 뺀 다음, 남은 leaf의 마지막 키를 `deleteData`로 **삭제하면서** 그것을 승격 키로 썼다.

```kotlin
// 수정 전
fun split(): NodeSplitData {
    val promotionKeyIdx = promotionKeyIdx()
    val (splitKeys, splitValues) = splitData(promotionKeyIdx)
    val totalRecordCount = page.recordCount
    val (promotionKey, _) = page.deleteData(totalRecordCount - 1)   // leaf에서 제거됨
    return NodeSplitData(splitKeys, splitValues, promotionKey, -1)
}
```

internal node는 승격 키를 부모로 "이동"하지만, leaf는 "복사"해야 한다.
여기서는 internal node 방식을 그대로 썼다.
또 `splitData` 이후의 `recordCount` 기준으로 슬롯을 접근하고 있어서 이미 옮겨진 뒤의 슬롯을 읽을 위험도 있었다.

## 수정

승격 키를 오른쪽 노드의 첫 번째 키(복사본)로 하고, 왼쪽 leaf에서는 아무것도 지우지 않는다.

```kotlin
// 수정 후
fun split(): NodeSplitData {
    val promotionKeyIdx = promotionKeyIdx()
    val (splitKeys, splitValues) = splitData(promotionKeyIdx)
    val promotionKey = splitKeys.first()
    return NodeSplitData(splitKeys, splitValues, promotionKey, -1)
}
```

분할 경계는 왼쪽 `[0 .. promotionKeyIdx]`, 오른쪽 `[promotionKeyIdx+1 .. end]`이고, 부모 separator는 오른쪽 노드의 최소 키다.

## 관련

- BUG-003: in-memory 구현 시절의 같은 계열(leaf promotion key 위치) 수정
- BUG-011: split 후 새 키를 넣을 방향 판단 (`getData(promotionKeyIdx + 1)` 기준, 이 수정과 같은 경계를 사용)
- BUG-029: 같은 커밋의 `splitData` 루프 수정
