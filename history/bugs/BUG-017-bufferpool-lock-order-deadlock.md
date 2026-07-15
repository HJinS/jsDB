# BUG-017 BufferPoolManager — Lock 취득 순서 불일치 (잠재적 위험, 현재는 안전)

- **커밋:** 해당 없음
- **날짜:** 2026-07-12 (분석)
- **컴포넌트:** `BufferPoolManager.kt` — `fetchPage`, `deletePage`
- **상태:** 의도적 설계, 현재 코드에서는 안전

## 증상

`deletePage`와 `fetchPage`의 락 취득 순서가 달라 코드 리뷰 시 잠재적 데드락으로 오인할 수 있음.

```
fetchPage:   globalLatch → 해제 → frame.latch
deletePage:  globalLatch → frame.latch (globalLatch 유지한 채)
```

## 원인 분석

### deletePage가 globalLatch 안에서 frame.latch를 잡는 이유

의도적인 설계. `globalLatch`를 해제한 뒤 `frame.latch`를 잡으면 다음 경합 발생 가능:

```
1. deletePage: pageTable.remove(pageId) → globalLatch 해제
2. [찰나] 다른 스레드: getFreeFrameId() → replacer.evict() → 동일 frameId 반환
   (freeList에는 없지만 replacer는 pinCount=0인 frame을 evict 후보로 봄)
3. 다른 스레드: 해당 frame을 새 페이지로 덮어쓰기 시작
4. deletePage: 같은 frame에 reset 진행 → 데이터 손상
```

`globalLatch`를 유지한 채 `frame.latch`까지 선점함으로써 이 window를 제거.

### 현재 코드에서 데드락이 발생하지 않는 이유

데드락 조건: Thread A가 `frame.latch`를 들고 `globalLatch`를 기다리는 경로가 있어야 함.

`PageLock.close()` 구현:
```kotlin
override fun close() {
    unlock()                               // frame.latch 먼저 해제
    bufferPoolManager.unpinPage(...)      // 그 다음 globalLatch 취득
}
```

`frame.latch`를 해제한 뒤에야 `globalLatch`를 잡으므로 사이클이 생기지 않음.
현재 코드 어디에도 `frame.latch`를 보유한 채 `globalLatch`를 취득하는 경로가 없음.

## 잠재 위험

향후 `asWriteView` / `asReadView` 블록 안에서 BPM 메서드(`unpinPage`, `fetchPage` 등)를 직접 호출하는 코드가 추가될 경우:

```
Thread A: frame.latch 보유 (asWriteView 안) → globalLatch 대기 (BPM 메서드 호출)
Thread B: globalLatch 보유 (deletePage)    → frame.latch 대기
→ 데드락
```

## 결론

현재 구현은 안전하며 수정 불필요. 단, BPM 메서드를 `asWriteView` / `asReadView` 내부에서 호출하는 코드를 추가할 때는 이 설계를 인지하고 주의 필요.
