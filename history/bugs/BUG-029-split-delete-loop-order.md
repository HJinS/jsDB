# BUG-029 splitData / deleteAllData - 정방향 삭제 루프로 인한 슬롯 건너뜀 및 순서 역전

- **커밋:** `719d3e6`
- **날짜:** 2026-05-04
- **컴포넌트:** `LeafNode.kt`, `InternalNode.kt` — `splitData`, `deleteAllData`, `InternalNode.split`
- **상태:** 수정 완료

## 증상

split이나 merge 때 노드에서 꺼낸 키/값 목록이 절반만 채워지거나 순서가 뒤집혔다.
옮겨진 노드에서 키가 유실되거나 정렬이 깨져 이후 탐색이 틀어졌다.

## 원인

노드에서 여러 레코드를 꺼내는 루프가 앞쪽 슬롯부터 `deleteData`를 호출했다.

```kotlin
// 수정 전 (LeafNode.splitData, InternalNode.splitData)
for(slotId in promotionKeyIdx until totalRecordCount){
    val (key, value) = page.deleteData(slotId)
    keyList.add(key)
    values.add(value)
}

// 수정 전 (deleteAllData)
for(slotId in 0 .. endSlotId){
    val (key, value) = page.deleteData(slotId)
    resultKey[slotId] = key
    resultValue[slotId] = value
}
```

`deleteData`는 뒤 슬롯을 앞으로 당기므로(BUG-028) 다음 반복의 `slotId`는 이미 한 칸 앞선 데이터를 가리킨다.
그 결과 슬롯이 하나씩 건너뛰어지고, 반복 횟수(`totalRecordCount`)는 줄어든 recordCount와 어긋난다.

`InternalNode.split`은 순서도 문제였다.
승격 키를 `splitData`가 끝난 뒤의 `recordCount`로 계산해 `deleteData(totalRecordCount - 1)`로 꺼냈기 때문에, `splitData`의 삭제 방식에 그대로 의존했다.

## 수정

- 뒤에서 앞으로 순회하고 결과는 `addFirst`로 쌓아 원래 순서를 유지한다.
- `deleteAllData`도 같은 방식으로 바꾸고, `InternalNode`의 leftMost child 값은 마지막에 `addFirst`.
- `InternalNode.split`은 승격 키/자식을 `splitData` **이전에** `getData(promotionKeyIdx)`로 읽고, `splitData` 이후 `deleteData(promotionKeyIdx)`로 제거한다.

```kotlin
// 수정 후
for (slotId in totalRecordCount - 1 downTo promotionKeyIdx + 1) {
    val (key, value) = page.deleteData(slotId)
    keyList.addFirst(key)
    values.addFirst(value)
}
```

## 참고

같은 날 두 커밋으로 나뉘어 반영됐다. `deleteData`가 슬롯을 당기는 변경은 1시간 뒤의 `f1426a0`(BUG-028)이고, 이 커밋(`719d3e6`)은 그 동작을 전제로 노드 쪽 루프를 먼저 고쳤다.

## 관련

- BUG-014: split 후 compaction 누락 (같은 `splitData`의 후속 수정)
- BUG-030: 같은 `split()`의 promotion key 처리
