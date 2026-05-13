# BufferPoolManager & Frame 동시성 개선 계획

## 1. 현재 구현의 문제점 및 개선 방향

### A. 매니저 전체 잠금(Global Latch) 병목
- **현상:** `fetchPage`, `unpinPage`, `flushPage` 등 대부분의 주요 작업이 `globalLatch` 하나에 의존하고 있어 멀티 스레드 환경에서 처리량이 저하됨.
- **개선:** `globalLatch`는 매니저의 공유 자료구조(`pageTable`, `freeList`)를 수정할 때만 아주 짧게 잡고, 실제 데이터 I/O나 상태 변경은 Frame 단위의 Lock/Atomic 연산으로 위임.

### B. PinCount 관리의 원자성 부족
- **현상:** `volatile Int`는 가시성은 보장하지만 `count++`와 같은 복합 연산의 원자성을 보장하지 않음. `globalLatch` 외부에서 조작 시 데이터 유실 가능성.
- **개선:** `AtomicInteger`를 사용하여 CAS(Compare-And-Swap) 방식으로 안전하게 증감.

### C. 디스크 I/O 시 데이터 정합성 위험
- **현상:** `flushPage` 등에서 디스크에 데이터를 쓸 때 해당 Frame의 `latch`를 획득하지 않음. 쓰기 작업 중 다른 스레드가 데이터를 수정할 경우 손상된 데이터가 저장될 수 있음.
- **개선:** `diskManager` 호출 전 반드시 Frame의 `readLock`(읽어서 쓰기) 또는 `writeLock`(읽어오기)을 획득.

---

## 2. 세부 개선 전략

### 2.1 AtomicInteger & CAS 활용
`pinCount`를 `AtomicInteger`로 변경하고, `globalLatch` 범위를 줄이기 위한 로직 예시:

```kotlin
// Frame.kt
val pinCount = AtomicInteger(0)

// BufferPoolManager.kt (fetchPage 일부)
fun pinFrame(frame: Frame): Boolean {
    while (true) {
        val current = frame.pinCount.get()
        // 이미 교체 대상(Eviction)으로 선정되어 pageId가 바뀌는 중인지 체크하는 로직 필요
        if (frame.pinCount.compareAndSet(current, current + 1)) {
            return true
        }
    }
}
```

### 2.2 디스크 I/O 보호 (Frame Latch)
`flushPage` 수행 시 데이터 일관성 보장:

```kotlin
fun flushPage(pageId: Long) {
    val frame = findFrame(pageId) ?: return
    
    // 데이터를 디스크에 쓰는 동안 수정되지 않도록 ReadLock 획득
    frame.latch.readLock().lock()
    try {
        if (frame.isDirty) {
            diskManager.writePage(pageId, frame.data)
            frame.isDirty = false
        }
    } finally {
        frame.latch.readLock().unlock()
    }
}
```

### 2.3 Double-Checked Locking 패턴
매니저 Lock의 체류 시간을 줄이기 위한 접근:

1.  **1차 확인:** `globalLatch`를 잡고 `pageTable`에서 Frame 존재 여부 확인.
2.  **Hit 시:** `pinCount` 올리고 즉시 `globalLatch` 해제.
3.  **Miss 시:** `evict` 로직 수행(이때는 Lock 유지), 새로운 Frame 할당 후 `pageTable` 등록 후 `globalLatch` 해제.
4.  **I/O 수행:** 해제된 상태에서 할당받은 Frame의 `writeLock`을 잡고 디스크에서 읽어옴.

---

## 3. 요약된 체크리스트
- [ ] `Frame.pinCount`를 `AtomicInteger`로 변경
- [ ] `flushPage` 호출 시 `frame.latch` 획득 로직 추가
- [ ] `newPage`에 `globalLatch` 적용 및 동기화 범위 최적화
- [ ] `fetchPage` 내 I/O 로직을 `globalLatch` 외부로 완전히 분리 (이미 일부 적용됨)
- [ ] `PageLock`의 `close`(`unpin`) 시점에 `globalLatch` 없이 `Atomic` 연산으로 처리 가능한지 검토
