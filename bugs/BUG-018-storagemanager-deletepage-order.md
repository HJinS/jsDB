# BUG-018 StorageManager.deletePage — 작업 순서 오류로 인한 InvalidReadOffsetException

- **커밋:** 119e3b7d
- **날짜:** 2026-07-12 (수정)
- **컴포넌트:** `StorageManager.kt` — `deletePage`
- **상태:** 수정 완료

## 증상

B+ 트리에서 delete 후 merge가 발생할 때 다음 예외로 크래시:

```
FreeSpaceManager.addFreePageID
→ BufferPoolManager.fetchPage
→ DiskManager.readPage
→ InvalidReadOffsetException: PageId: 29 not exist
```

## 원인

`StorageManager.deletePage`의 작업 순서 오류:

```kotlin
// 수정 전 (버그)
fun deletePage(pageId: Long){
    if(pageId <= 0L) throw StorageManagerException.InvalidPageIdException(pageId)
    bufferPoolManager.deletePage(pageId)   // ← 1. pageTable에서 제거, frame 리셋
    freeSpaceManager.addFreePageID(pageId) // ← 2. 삭제된 페이지를 fetchPage로 접근 → 크래시!
}
```

`addFreePageID`는 내부에서 `bufferPoolManager.fetchPage(pageId)`를 호출해 freed page의 `LEFT_SIBLING_PAGE_ID` 필드에 free list next pointer를 기록한다. 그런데 `bufferPoolManager.deletePage`가 먼저 실행되면:

1. `pageTable`에서 해당 pageId 제거
2. frame.data 리셋 (메모리 초기화)
3. freeList에 frameId 반납

이후 `addFreePageID`가 fetchPage를 시도할 때:
- pageTable에 해당 pageId 없음 → disk read 시도
- disk에도 valid한 페이지가 없으면 `InvalidReadOffsetException` 발생

## 수정

작업 순서 변경: free list pointer 기록 → disk flush → frame 회수

```kotlin
// 수정 후
fun deletePage(pageId: Long){
    if(pageId <= 0L) throw StorageManagerException.InvalidPageIdException(pageId)
    freeSpaceManager.addFreePageID(pageId)  // 1. buffer에 있는 동안 free list pointer 기록
    bufferPoolManager.flushPage(pageId)     // 2. pointer를 disk에 flush (getFreePageID가 disk read 시 필요)
    bufferPoolManager.deletePage(pageId)    // 3. frame 회수
}
```

### 왜 flushPage가 필요한가

`addFreePageID`가 `LEFT_SIBLING_PAGE_ID`에 next pointer를 기록한 직후 frame을 unpin한다. 이 frame은 evict 가능 상태가 되며, 이후 `bufferPoolManager.deletePage`가 frame을 리셋하면 in-memory 수정 사항이 사라진다. 나중에 `getFreePageID`가 이 page를 disk에서 다시 읽을 때 next pointer를 찾지 못하게 된다.

`flushPage`는 `deletePage`(frame 리셋) 전에 수정된 data를 disk에 기록함으로써 free list chain의 일관성을 보장한다.

## 스택 트레이스 (재현 당시)

```
io.kotest.assertions.AssertionFailedError: → storageEngine.exception.StorageManagerException$InvalidReadOffsetException
  at storageEngine.DiskManager.readPage(DiskManager.kt:42)
  at storageEngine.BufferPoolManager.fetchPage(BufferPoolManager.kt:113)
  at storageEngine.FreeSpaceManager.addFreePageID(FreeSpaceManager.kt:42)
  at storageEngine.StorageManager.deletePage(StorageManager.kt:62)
  at index.btree.BTree.handleUnderflow(BTree.kt:263)
```

## 관련 설계 메모

`FreeSpaceManager`는 freed page의 `LEFT_SIBLING_PAGE_ID` 필드를 free list의 next pointer로 재활용한다. 따라서 deletePage 시에 해당 page가 반드시 buffer pool에 살아있어야 pointer를 기록할 수 있다.
