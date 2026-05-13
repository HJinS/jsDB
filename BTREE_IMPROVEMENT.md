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

---

## 4. 2차 검토 결과 (추가 발견된 이슈)

일부 개선 사항이 반영되었으나, 코드 안정성을 위해 아래 항목들의 추가 수정이 필요합니다.

### 4.1 `SlottedPage.binarySearch` 논리 오류

- **문제**: `compareResult < 0` (찾는 키가 중앙값보다 작음)일 때 `low = mid + 1`로 이동하고 있습니다.
- **영향**: 오름차순 정렬 상태에서 작은 값을 찾으려 할 때 오히려 큰 쪽을 탐색하게 되어 결과를 찾지 못합니다.
- **해결**: `compareResult < 0`이면 `high = mid - 1`, `compareResult > 0`이면 `low = mid + 1`이 되어야 합니다. 또한 `while(low <= high)`로 수정하여 모든 요소를 검사해야 합니다.

### 4.2 `Node` 클래스의 중복 및 버그성 메서드 (`deleteData` vs `deleteAt`)

- **문제**: `Node.deleteData(slotId)`가 인자로 받은 `slotId`를 무시하고 무조건 `page.deleteData(0)`을 호출합니다.
- **영향**: 특정 인덱스의 데이터를 지우려 할 때 항상 0번 데이터가 지워져 트리가 망가집니다.
- **해결**: `deleteAt`으로 이름을 통일하고, 인자를 올바르게 전달하는 구현만 남겨야 합니다.

### 4.3 Split 로직의 순서 및 인덱스 참조 오류

- **InternalNode**: `splitData` 시 `keyList.add(key)`를 사용하면 역순(`downTo`) 루프 때문에 데이터가 거꾸로 들어갑니다. `addFirst`를 사용해야 합니다.
- **LeafNode**: `split()` 메서드에서 `splitData`를 먼저 호출하여 데이터를 지운 후 `page.getData(totalRecordCount - 1)`을 호출합니다. 이미 삭제된 슬롯에 접근하므로 `IndexOutOfBounds` 예외가 발생하거나 잘못된 키를 참조합니다.
- **Promotion Key**: B+Tree 리프 분할 시 부모로 올라가는 키는 **우측 노드의 최소 키**여야 합니다. 현재는 좌측 노드에 남은 마지막 키를 참조할 가능성이 큽니다.

### 4.4 여전히 미해결된 항목

- **Latch Crabbing**: `searchLeafNode`에서 자식 노드 획득 전 부모 락을 해제하는 구조가 남아있습니다.
- **Root ID 지속성**: `rootPageId`를 파일 헤더에 기록하고 로드하는 로직이 아직 없습니다.

---

## 5. 3차 검토 결과 (논리 결함 완결 및 구조적 과제)

3차 검토 결과, 대부분의 로직 버그가 해결되었으며 이제 엔진의 **영속성(Persistence)**과 **병렬성(Concurrency)** 최적화 단계로 진입할 준비가 되었습니다.

### 5.1 수정 완료 사항 (Verified)

- **리프 노드 데이터 정합성 (2.3 해결)**: `LeafNode.split` 시 `promotionKey`가 우측 형제 노드의 첫 번째 키로 복사되어 리프 노드에 데이터가 온전히 보존됩니다.
- **InternalNode 분할 로직 완성**: `splitData` 시 `addFirst`를 사용하여 정렬 순서 문제를 해결했으며, 분할 시 자식 포인터 이동 로직이 표준 B+Tree 규칙에 맞게 정립되었습니다.
- **삭제/이동 로직 중복 제거**: `redistribute` 등에서 `deleteData` 호출 후 불필요하게 `shiftSlot`을 중복 호출하던 로직을 제거하여 슬롯 파손 위험을 없앴습니다.
- **이진 탐색 및 삭제 메서드 정상화**: `SlottedPage.binarySearch`의 방향 오류와 `Node.deleteData`의 인덱스 무시 버그가 모두 해결되었습니다.

### 5.2 향후 핵심 과제 (Next Steps)

1. **Latch Crabbing 구현 (구조적 변경)**:
   - 현재 `BTree.searchLeafNode`는 `use` 블록을 사용하여 부모 락을 즉시 해제합니다.
   - 자식 페이지를 안전하게 확보할 때까지 부모의 `PageLock`을 유지하는 방식으로 `searchLeafNode`의 루프 구조를 개편해야 합니다.

2. **루트 페이지 ID 영속화**:
   - `rootPageId`가 `-1`로 초기화되는 문제를 해결하기 위해, `DiskManager`나 `HeaderPage`(0번 페이지)에 루트 위치를 저장하고 로드하는 기능을 추가해야 합니다.

3. **버퍼 풀 핀(Pin) 최적화**:
   - 탐색 경로에 있는 페이지들을 반복적으로 `fetch/unpin`하는 대신, 작업이 끝날 때까지 핀을 유지하여 I/O 효율을 높여야 합니다.

---

## 6. Latch Crabbing (Lock Coupling) 설계 사양

병렬 성능 최적화 및 데이터 정합성 보장을 위해 다음과 같은 Latch Crabbing 전략을 도입합니다.

### 6.1 PageLock 고도화

- **명시적 락 제어**: `lockRead()`, `lockWrite()`, `unlock()` 메서드를 추가하여 외부에서 락의 수명을 명시적으로 제어할 수 있도록 합니다.
- **View 로직 분리**: `asReadView`, `asWriteView` 내부의 자동 락/언락 로직을 제거하여, 락 획득 여부와 데이터 접근을 독립적으로 처리합니다.
- **자원 회수 보장**: `close()` 호출 시 미해제된 락이 있다면 자동으로 해제하고 `unpin`을 수행하여 리소스 누수를 방지합니다.

### 6.2 BTreeLatchCrab (자원 및 정책 관리자)

**역할**: 탐색 경로상의 `PageLock` 스택 관리 및 `canAbsorb` 조건에 따른 조상 노드 락 해제 정책 수행.

- **인스턴스 생명주기**:
  - 하나의 트리 탐색(Descent) 과정마다 하나의 인스턴스를 생성하여 사용.
  - `LockMode`를 생성자 인자로 받아 해당 탐색의 성격(READ/WRITE)을 고정함.
  - Kotlin의 `use` 블록을 사용하여 탐색 종료 시 모든 잔여 락을 자동으로 해제 (`close()`).
- **자료구조**: `ArrayDeque<PageLock>` (루트부터 순차적으로 핸들 누적).
- **물리적 락 모드**: `READ` (Shared), `WRITE` (Exclusive) 두 가지만 사용하여 동기화에 집중.
- **핵심 메서드**:
  - `lockAndPush(lock)`: 자식 노드의 락을 획득한 후 즉시 큐의 마지막(Last)에 추가.
  - `releaseAncestors(lock)`: **FIFO(First-In-First-Out) 순서**로 조상 락 해제. 큐의 가장 앞에 있는 루트(Root)부터 차례대로 `close()`하여 상위 노드의 병목을 최우선으로 해소.
  - `close()`: 탐색 종료 또는 예외 발생 시 큐에 남은 모든 핸들을 해제하여 자원 누수 방지.

### 6.3 Operation별 Safe 조건 (`node.canAbsorb(mode)`)

'Safe'의 핵심은 **"하위 노드의 변화(Split/Merge)가 부모 노드까지 전파될 가능성이 있는가?"**입니다.

| 연산 모드  | Safe 조건 (`canAbsorb`) | 상세 설명                                                                               |
| :--------- | :---------------------- | :-------------------------------------------------------------------------------------- |
| **READ**   | `true` (항상 안전)      | 조회를 위한 하강은 구조 변경을 일으키지 않으므로 자식 획득 즉시 부모 해제 가능.         |
| **INSERT** | `keyCount < maxKeys`    | 현재 노드에 여유 공간이 있다면 삽입 시 분할(Split)이 발생하지 않아 부모가 안전함.       |
| **DELETE** | `keyCount > minKeys`    | 삭제 후에도 노드가 최소 키 개수를 유지한다면 병합(Merge)이 발생하지 않아 부모가 안전함. |
| **UPDATE** | `keyCount > minKeys`    | **삭제 후 삽입** 방식의 경우, 먼저 일어나는 삭제 연산에서 병합이 발생하지 않아야 함.    |

> **참고**: `minKeys`는 일반적으로 `maxKeys / 2`를 기준으로 함.

### 6.4 알고리즘 흐름 (searchLeafNode)

1. `BTreeLatchCrab` 인스턴스 생성 (`use` 블록).
2. 루트부터 자식으로 내려가며 `storageManager.fetchPage(id)` 수행.
3. `crab.lockAndPush(lock)`로 락 획득 및 관리 위임.
4. `node.canAbsorb(mode)`로 현재 노드의 안전성 확인.
5. 안전하다면 `crab.releaseAncestors(lock)`로 조상 락 일괄 해제.
6. 리프 노드 도달 시 해당 핸들 반환.
7. 연산 종료 시 `crab.close()`를 통해 모든 자원 자동 반납.

---

## 7. 가변 길이 데이터 및 물리적 공간 관리

현재의 `keyCount` 기반 관리에서 실제 페이지 용량(`pageSize`) 기반 관리로 고도화하기 위한 설계 사양입니다.

### 7.1 물리적 공간 체크 (`isSafe` 확장)

가변 길이 데이터를 사용하는 `SlottedPage` 구조에서는 키 개수가 적더라도 물리적 공간이 부족할 수 있습니다.

- **조건 추가**: `canAbsorb` 판단 시 `(keyCount < maxKeys)` 조건뿐만 아니라 `(freeSpace >= requiredBytes)` 조건을 **AND**로 체크해야 합니다.
- **정보 전달**: `BTree.insert` 시작 시 키/밸류를 미리 직렬화하여 필요한 `requiredBytes` 정보를 탐색 로직(`canAbsorb`)에 전달해야 합니다.

### 7.2 페이지 헤더(Page Header) 동기화

페이지의 여유 공간 상태를 효율적으로 파악하기 위해 다음 정보를 각 페이지의 헤더에 저장합니다.

- **저장 필드**:
  - `freeSpaceOffset`: 데이터가 저장될 다음 빈 위치의 오프셋.
  - `remainingBytes`: 현재 페이지에서 사용 가능한 순수 여유 공간.
  - `slotCount`: 현재 저장된 레코드 수.
- **지속성**: 위 필드들은 `SlottedPage` 수정 시마다 업데이트되며, 페이지가 디스크에 써질 때 함께 저장(Persistence)됩니다.

### 7.3 Split/Merge 기준의 변화

- **Split**: `keyCount >= maxKeys` 이거나 `freeSpace < requiredBytes` 인 경우 발생. (OR 조건)
- **Merge**: 구현 단순화를 위해 `keyCount < minKeys` (논리적 개수) 기준을 우선 유지하되, 향후 페이지 사용률(%) 기반으로 확장 가능.

---

## 8. 계층 간 잠금 및 자원 관리 최적화 (2026-05-05)

`BTree` -> `StorageManager` -> `BufferPoolManager`로 이어지는 호출 흐름에서 발생하는 잠금 중복과 비효율을 해결하기 위한 설계 사양입니다.

### 8.1 계층별 책임 분리 (Lock Ownership)

현재 여러 계층에서 개별적으로 `PageLock`을 획득/해제하여 발생하는 오버헤드를 제거하고, 자원 관리의 주도권을 상위 계층으로 일원화합니다.

- **BufferPoolManager (제공자)**:
  - 시스템 관리 자원(`pageTable`, `replacer`) 보호를 위한 `globalLatch`는 원자적 연산 시에만 아주 짧게 유지.
  - 디스크 I/O 작업 시에는 `globalLatch`를 해제하고 개별 `frame.latch`만 점유하여 병렬성 극대화.
  - 요청된 `LockMode`에 맞춰 락을 **획득한 상태**로 `PageLock`을 반환. (I/O 완료 후 스스로 풀지 않음)
- **StorageManager (중계자)**:
  - 내부에서 `.use { ... }` 블록을 통한 조기 해제 금지.
  - `BPM`으로부터 받은 락이 유지된 `PageLock`을 `BTree`로 그대로 전달.
- **BTree / LockManager (소유자)**:
  - 전달받은 `PageLock`의 수명 주기를 전적으로 책임짐.
  - `LockManager`가 큐에 담긴 모든 락을 작업 완료 시점에 한 번에 해제(`unlock`) 및 반납(`unpin`).

### 8.2 Lock Mode 전파 및 강등 (Lock Downgrading)

`BTree`의 탐색 의도(읽기/쓰기)를 최하위 계층까지 전달하여 불필요한 락 전환 비용을 줄입니다.

- **인자 전달**: `fetchPage(pageId, lockMode)`와 같이 `LockMode`를 명시적으로 전달.
- **선제적 락 결정**: `BPM`은 페이지를 메모리에 올리는 즉시 요청된 모드에 맞춰 락을 설정하여 반환.
- **잠금 강등 (Downgrading)**:
  - `BPM`이 디스크에서 데이터를 읽어올 때는 반드시 `WriteLock`이 필요함.
  - 읽기 요청(`READ`)인 경우, I/O 완료 후 `WriteLock`을 유지한 상태에서 `ReadLock`을 획득하고 `WriteLock`을 해제하는 **원자적 강등**을 수행하여 정합성 유지.

### 8.3 주요 메서드별 처리 원칙

| 메서드 | 락 유지 여부 | 설명 |
| :--- | :--- | :--- |
| **fetchPage** | **유지** | 호출자의 `LockMode`에 맞춰 락을 잡은 채 반환. |
| **newPage** | **유지** | 초기화가 필요하므로 항상 `WriteLock`을 잡은 채 반환. |
| **deletePage** | 해제 | 자원 소멸 작업이므로 내부에서 `pinCount` 체크 및 정리 후 락 없이 종료. |
| **unpin/flush** | 해제 | 단순 상태 업데이트 및 기록 작업이므로 내부 처리 후 종료. |

### 8.4 기대 효과

1. **데드락 및 레이스 컨디션 방지**: `StorageManager`에서의 조기 `unpin`으로 인한 데이터 유실 위험 제거.
2. **성능 최적화**: 동일 프레임에 대한 반복적인 `unlock -> lock` 호출 제거 및 `globalLatch` 경합 감소.
3. **코드 가독성**: `BTree` 로직 내의 지저분한 중첩 `.use` 블록이 사라지고 `LockManager`를 통한 선형적인 자원 관리 가능.
