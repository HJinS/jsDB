# BUG-019 SlottedPage.getData() bounds check 누락 — 잔존 슬롯 바이트로 인한 isLeft() 오판

- **커밋:** 119e3b7d
- **날짜:** 2026-07-15
- **컴포넌트:** `SlottedPage.kt` — `getData`, `Node.kt` — `isLeft`
- **상태:** 수정 완료

## 증상

B+ 트리에서 delete → underflow → merge 흐름이 발생하는 특정 루프에서 간헐적으로 트리 구조가 손상되어 이후 search/traverse가 틀린 값을 반환하거나 예외를 던짐. 증상이 비결정론적(셔플 순서에 따라 실패 루프 번호가 달라짐)으로 나타남.

## 원인

### SlottedPage의 deleteData 후 슬롯 바이트 잔존

`deleteData(slotId)`는 슬롯 배열을 왼쪽으로 shift하고 `recordCount`를 감소시키지만, 이전 마지막 슬롯이 있던 바이트는 **0으로 초기화하지 않는다**.

```
deleteData(slot 2) 전:  [slot0][slot1][slot2][slot3]  recordCount=4
deleteData(slot 2) 후:  [slot0][slot1][slot3][slot3]  recordCount=3
                                               ↑ 잔존 바이트 (이전 slot3의 복사본)
```

shift는 `src → dst` 방향으로만 데이터를 이동하며, shift 이후 배열 끝 쪽에 이전 슬롯의 `(offset, length)` 바이트가 남는다.

### getData에 recordCount 경계 체크 없음

수정 전 `getData(slotId)`:

```kotlin
fun getData(slotId: Int): Pair<ByteArray, ByteArray>{
    val slotLocation = HEADER_SIZE + slotId * SLOT_SIZE
    val offset = data.getShort(slotLocation)
    val length = data.getShort(slotLocation + 2)
    if(length.toInt() == 0) throw SlottedPageException.SlotOutOfBoundException(...)
    // ...
}
```

`slotId >= recordCount`인 경우 예외를 던지지 않고, 잔존 바이트에서 `offset`과 `length`를 읽어 **쓰레기 데이터를 반환**했다. 잔존 바이트의 `length`가 0이 아니면 조용히 garbage 데이터를 반환.

### isLeft()의 오판

`Node.isLeft(targetPageId, parentNode, keyIdx)`는 merge 시 sibling이 left인지 right인지 판별한다:

```kotlin
fun isLeft(targetPageId: Long, parentNode: InternalNode<K>, keyIdx: Int): Boolean{
    return try {
        val rightChildId = parentNode.childPageId(keyIdx + 1)  // → getData(keyIdx) 호출
        targetPageId == rightChildId
    } catch (_: SlottedPageException.SlotOutOfBoundException){
        val leftChildId = parentNode.childPageId(keyIdx - 1)
        targetPageId != leftChildId
    }
}
```

`childPageId(keyIdx + 1)`가 `getData(keyIdx)`를 호출하는데, `keyIdx >= recordCount`인 경우:
- **수정 전**: 잔존 바이트에서 garbage pageId를 읽어 반환 → `targetPageId == garbageId`가 우연히 true/false → merge 방향(left/right) 오판
- **수정 후**: `SlottedPageException.SlotOutOfBoundException` 발생 → catch 블록에서 left sibling 기준으로 올바르게 판별

### merge 방향 오판의 결과

`orderNode`가 잘못된 `(separationKeyIdx, leftNode, rightNode)`를 반환하면:
- 잘못된 victim이 선택되어 살아야 할 페이지가 삭제됨
- parent에서 제거되는 separator가 틀려 parent가 존재하지 않는 pageId를 계속 참조
- 이후 search 시 해당 stale pageId로 이동해 구조 손상 또는 예외 발생

## 수정

`getData` 시작 부분에 명시적 경계 체크 추가:

```kotlin
fun getData(slotId: Int): Pair<ByteArray, ByteArray>{
    if(slotId < 0 || slotId >= recordCount)
        throw SlottedPageException.SlotOutOfBoundException(slotId, pageId, type)
    // ...
}
```

## 파급 효과

이 수정으로 `isLeft()`의 오판은 해결됐으나, 기존에 묵살되던 두 버그가 표면화됨 → **BUG-021** 참조.

## 관련

- BUG-021: `BTree.search` — `isExist=false` 시 `getData(keyIdx)` 무조건 호출 (본 버그 수정으로 표면화)
