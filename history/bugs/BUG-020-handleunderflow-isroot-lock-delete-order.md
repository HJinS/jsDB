# BUG-020 handleUnderflow isRoot 블록 — lock release 전 deletePage 호출 → PageInUseException

- **커밋:** 119e3b7d
- **날짜:** 2026-07-15
- **컴포넌트:** `BTree.kt` — `handleUnderflow`
- **상태:** 수정 완료

## 증상

delete cascade가 root까지 전파되어 root 노드가 shrink(0-key internal node → 새 root 승격)될 때 다음 예외 발생:

```
BufferPoolManagerException$PageInUseException: Page X is currently in use (pin count > 0)
```

## 원인

`handleUnderflow`의 `isRoot` 블록에서 `deletePage` 호출 전에 lock을 해제해야 하는데 순서가 반대였다.

```kotlin
// 수정 전 (버그)
if(needChangeRoot && newRootId != null){
    rootPageId = newRootId
    storageManager.deletePage(currentPageId)    // ← lock 해제 전 삭제 시도
    lockManager.closeAndRemoveLock(currentLock) // ← 여기서 unpin 해야 pin=0이 되는데...
}
```

`deletePage` 내부의 `bufferPoolManager.deletePage`는 `pinCount > 0`이면 `PageInUseException`을 던진다. `currentLock`이 아직 열려 있으므로 pin count = 1인 상태에서 삭제를 시도해 예외 발생.

### leaf merge victim 코드와의 비교

같은 `handleUnderflow` 내 leaf merge victim 처리는 올바른 순서였다:

```kotlin
// leaf merge victim 처리 — 올바른 순서
lockManager.closeAndRemoveLock(victimPageLock) // 1. unpin (pin: 1 → 0)
storageManager.deletePage(rightPageId)         // 2. pin=0 확인 후 삭제 ✓
```

isRoot 블록만 순서가 반대로 되어 있었다.

## 수정

```kotlin
// 수정 후
if(needChangeRoot && newRootId != null){
    rootPageId = newRootId
    lockManager.closeAndRemoveLock(currentLock) // 1. unpin (pin: 1 → 0)
    storageManager.deletePage(currentPageId)    // 2. pin=0 확인 후 삭제 ✓
}
```

## 관련

- BUG-018: `StorageManager.deletePage` 내부 작업 순서 오류 (같은 컴포넌트의 유사 패턴 버그)
