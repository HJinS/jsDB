# BUG-028 SlottedPage.deleteData - 삭제 후 빈 틈

- **커밋:** `f1426a0`
- **날짜:** 2026-05-04
- **컴포넌트:** `SlottedPage.kt` — `deleteData`, `insertSlot`, `getFreeSlotId`(제거)
- **상태:** 수정 완료

## 증상

레코드를 삭제하면 슬롯 배열 중간에 빈 슬롯(hole)이 남았다.
기존의 `FreeSlotId`를 관리하고 사용하는 방식의 경우 경우 `binarySearch`를 정상적으로 처리할 수가 없다.

## 원인

수정 전 `deleteData`는 해당 슬롯의 `(offset, length)`를 0으로 덮고, 그 슬롯을 free slot 리스트(헤더의 `FREE_SLOT_HEAD`)에 연결해 재사용하는 방식이었다.

```kotlin
// 수정 전
fun deleteData(slotId: Int): Pair<ByteArray, ByteArray>{
    val slotLocation = HEADER_SIZE + slotId * SLOT_SIZE
    val (key, value) = getData(slotId)
    data.putShort(slotLocation, 0)
    data.putShort(slotLocation+2, 0)
    retrieveFreeSlotId(slotId)   // free slot 리스트에 연결
    decreaseRecordCount()
    return key to value
}
```

B+Tree 노드에서는 "슬롯 순서 = 키 정렬 순서"여야 한다.
그런데 이 방식은 슬롯 번호를 안정된 ID처럼 다루는 힙 페이지식 설계라서, 정렬된 슬롯 배열이 필요한 노드 구조와 맞지 않았다.

## 수정

- `deleteData`가 삭제 슬롯 뒤의 슬롯들을 한 칸씩 앞으로 당기도록 변경 (`shiftSlot(slotId + 1, recordCount - (slotId + 1), -1)`).
- `insertSlot`도 직접 `ByteBuffer.put`하던 코드를 `shiftSlot(index, recordCount - index, 1)` 호출로 통일.
- free slot 리스트 관련 코드(`getFreeSlotId`, `retrieveFreeSlotId` 호출) 제거.

```kotlin
// 수정 후
fun deleteData(slotId: Int): Pair<ByteArray, ByteArray>{
    val (key, value) = getData(slotId)
    if(slotId < recordCount - 1){
        shiftSlot(slotId+1, recordCount - (slotId + 1), -1)
    }
    decreaseRecordCount()
    return key to value
}
```

## 관련

- BUG-009: 같은 커밋(`f1426a0`)의 `shiftSlot`/`binarySearch` 수정
- BUG-019: 이 수정으로 배열 끝에 남는 잔존 슬롯 바이트가 `getData`에서 노출된 문제
- BUG-029: `deleteData`가 슬롯을 당기게 되면서 정방향 삭제 루프가 깨진 문제
