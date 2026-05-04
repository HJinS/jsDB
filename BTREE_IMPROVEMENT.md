# B-Tree (Disk-based) 분석 및 개선 리포트

`src/main/kotlin/index/btree/BTree.kt` 및 관련 스토리지 엔진 클래스들을 검토한 결과, 성능과 안정성을 위해 개선이 필요한 주요 항목들을 다음과 같이 정리합니다.

## 1. 동시성 및 스레드 안전성 이슈 (Critical)

### 1.1 `traceNode` 공유 문제
- **문제**: `BTree` 클래스의 멤버 변수로 선언된 `traceNode: Stack`이 모든 스레드에 의해 공유되고 있습니다.
- **영향**: 여러 스레드가 동시에 삽입/삭제를 수행할 경우 탐색 경로가 뒤섞여 트리가 파괴됩니다.
- **해결**: `traceNode`를 탐색 메서드(`searchLeafNode`)의 로컬 변수로 분리하고, 결과를 반환받아 작업 단위별로 관리해야 합니다.

### 1.2 Latch Crabbing 전략 부재
- **문제**: 페이지 탐색 후 락을 해제했다가 삽입 시 다시 락을 획득하는 구조입니다.
- **영향**: 락이 해제된 찰나에 다른 스레드가 노드를 수정(Split/Merge)하면 이전에 찾은 페이지 위치가 무효화됩니다.
- **해결**: 부모 노드의 락을 유지한 상태에서 자식 노드의 락을 획득하고, 자식이 안전(Safe)함이 확인되면 부모의 락을 해제하는 **Latch Crabbing** 도입이 필요합니다.

---

## 2. 데이터 정합성 및 로직 오류 (Critical)

### 2.1 `SlottedPage.deleteData` 슬롯 관리 오류
- **문제**: 데이터를 삭제할 때 슬롯 배열을 앞으로 당겨주는(Shift Left) 처리가 없습니다. 단순히 슬롯의 값을 0으로 설정합니다.
- **영향**: 슬롯 배열 중간에 빈 공간(Hole)이 생겨 `binarySearch`가 정상적으로 작동하지 않으며, 데이터가 있음에도 탐색에 실패하게 됩니다.
- **해결**: 삭제 시 `shiftSlot`을 호출하여 뒤쪽 슬롯들을 앞으로 당겨야 합니다.

### 2.2 Split/Merge 시 인덱스 밀림 현상
- **문제**: `splitData` 등에서 루프를 돌며 정방향(`0 -> n`)으로 `deleteData`를 호출합니다.
- **영향**: 삭제 시마다 데이터가 앞으로 당겨지므로 의도한 데이터의 절반만 처리되고 나머지는 유실됩니다.
- **해결**: 루프를 역순(`n -> 0`)으로 돌거나, 인덱스 변화를 고려한 삭제 로직으로 수정해야 합니다.

### 2.3 B+Tree 규칙 위반 (Leaf Node)
- **문제**: 리프 노드 분할 시 `promotionKey`를 리프에서 삭제하고 부모로만 올립니다.
- **영향**: B+Tree는 모든 실제 데이터가 리프에 존재해야 합니다. 현재 구조는 인덱스 탐색 시 실제 데이터를 유실하게 됩니다.
- **해결**: 리프 노드 분할 시 키를 삭제하지 말고 **복사(Copy)**하여 부모로 전달해야 합니다.

---

## 3. 지속성 및 리소스 관리

### 3.1 루트 페이지 ID 유실
- **문제**: `rootPageId`가 메모리 변수로만 존재합니다.
- **영향**: 시스템 재시작 시 루트 페이지 위치를 알 수 없어 인덱스를 다시 사용할 수 없습니다.
- **해결**: 파일의 0번 페이지(Header Page) 등에 `rootPageId`를 기록하고 초기화 시 로드해야 합니다.

### 3.2 중복된 페이지 Fetch
- **문제**: `searchLeafNode`나 `traverse` 시 동일한 페이지를 반복적으로 `fetch`하고 `unpin`합니다.
- **해결**: 탐색 과정에서 획득한 페이지 핸들을 재사용하거나, 버퍼 풀의 핀 관리 최적화가 필요합니다.

---

## 개선 코드 예시 (SlottedPage.deleteData)

```kotlin
// 수정 전: 슬롯에 0만 넣고 방치 (Hole 발생)
fun deleteData(slotId: Int): Pair<ByteArray, ByteArray>{
    val (key, value) = getData(slotId)
    data.putShort(slotLocation, 0)
    data.putShort(slotLocation+2, 0)
    decreaseRecordCount()
    return key to value
}

// 수정 후: 슬롯 배열을 밀어 정렬 유지
fun deleteData(slotId: Int): Pair<ByteArray, ByteArray> {
    val (key, value) = getData(slotId)
    if (slotId < recordCount - 1) {
        // 뒤의 슬롯들을 앞으로 한 칸씩 당김
        shiftSlot(slotId + 1, recordCount - (slotId + 1), -1)
    }
    decreaseRecordCount()
    return key to value
}
```
