# BUG-023 BufferPoolManager.fetchPage — pinCount 덮어쓰기 및 pageId 설정 타이밍 오류

- **커밋:** `1ac1ab9`
- **날짜:** 2026-04-23
- **컴포넌트:** `BufferPoolManager.kt` — `fetchPage`, `newPage`
- **상태:** 수정 완료

## 증상

동시 fetch가 발생하는 상황에서 이미 캐시에 올라와 있는 page를 두 스레드가 동시에 fetchPage할 경우 pinCount가 의도보다 낮게 유지됨. 이후 unpin이 먼저 발생하면 replacer가 해당 frame을 evict 대상으로 등록하여 사용 중인 page가 교체되는 문제.

## 원인

### 1. pageTable hit 경로 — pinCount 덮어쓰기

```kotlin
// 수정 전
if(pageTable.containsKey(pageId)) {
    val frameId = pageTable[pageId]!!
    frame = frames[frameId]
    replacer.pin(frameId)
    // pinCount 업데이트 없음 → 다른 스레드가 이미 pin한 경우 카운트 반영 안됨
}
```

pageTable에 이미 있는 경우 `replacer.pin()`만 호출하고 `frame.pinCount`는 건드리지 않음.
신규 fetch 경로에서는 `frame.pinCount = 1`로 **덮어쓰기** 했으므로, 스레드 A가 pinCount를 이미 1로 설정한 상태에서 스레드 B가 같은 page를 fetch하면 pinCount가 1로 리셋됨. 이후 A가 unpin → pinCount 0 → replacer가 evict 대상으로 등록 → B가 아직 사용 중인 frame 교체.

### 2. 신규 evict 경로 — pageId 설정이 globalLatch 해제 이후

```kotlin
// 수정 전 (globalLatch 해제 후, IO 진행 중)
pageTable.remove(frame.pageId)      // globalLatch 안
frame.pageId = pageId               // ← globalLatch 안에서 먼저 설정
frame.pinCount = 1
...
// IO 후
frame.reset()
diskManager.readPage(pageId, frame.data)
```

`frame.pageId = pageId`가 `frame.reset()` 이전에 설정됨. reset은 buffer를 초기화하는데, reset 전에 다른 스레드가 asReadView로 접근하면 이전 page 내용을 새 pageId로 읽는 상황 가능. 또한 `frame.pageId`가 일반 필드(non-atomic)여서 가시성 보장 없음.

### 3. newPage — globalLatch 없이 frame 조작

```kotlin
// 수정 전
fun newPage(pageID: Long): PageHandle {
    val emptyBuffer = ByteBuffer.allocateDirect(indexConfig.pageSize)
    diskManager.writePage(pageID, emptyBuffer)   // lock 없음
    val frame = frames[getFreeFrameId()]          // lock 없음
    frame.reset()
    frame.isDirty = true
    pageTable[pageID] = frame.frameId
    frame.pageId = pageID
    replacer.pin(frame.frameId)
    return PageHandle(frame, this)
}
```

`getFreeFrameId()`, `pageTable` 수정, `frame` 필드 접근이 모두 globalLatch 없이 수행됨. 동시 newPage 또는 fetchPage와 레이스 컨디션 발생.

## 수정

### fetchPage — pageTable hit 경로

```kotlin
// 수정 후
if(pageTable.containsKey(pageId)) {
    val frameId = pageTable[pageId]!!
    frame = frames[frameId]
    replacer.add(frameId)
    replacer.pin(frameId)
    frame.pinCount.incrementAndGet()  // 덮어쓰기 → atomic increment
}
```

### fetchPage — evict 경로: pageId를 writeLock 안에서 reset() 전에 설정

```kotlin
// 수정 후 (latch.writeLock 안에서)
if(victimPageId != null){
    diskManager.writePage(victimPageId, frame.data)
}
frame.pageId.set(pageId)   // ← reset() 전에, writeLock 보호 아래
frame.reset()
diskManager.readPage(pageId, frame.data)
```

`frame.pageId`를 AtomicLong으로 변경하고, 반드시 `reset()` 이전 + `latch.writeLock` 안에서 설정.

### newPage — globalLatch로 pageTable 조작 보호

```kotlin
// 수정 후
globalLatch.lock()
try{
    val freeFrameId = getFreeFrameId()
    frame = frames[freeFrameId]
    frame.latch.writeLock().lock()   // IO 전에 writeLock 선점
    ...
    pageTable.remove(currentPageId)
    pageTable[pageID] = freeFrameId
    frame.pinCount.set(1)
    replacer.pin(freeFrameId)
} finally{
    globalLatch.unlock()
}
// globalLatch 해제 후 writeLock 안에서 IO
try{
    if(victimPageId != null) diskManager.writePage(victimPageId, frame.data)
    frame.reset()
    frame.isDirty.set(true)
    frame.pageId.set(pageID)
} finally{
    frame.latch.writeLock().unlock()
}
```
