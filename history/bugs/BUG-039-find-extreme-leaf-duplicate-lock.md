# BUG-039 findExtremeLeafPageId — leaf에 도달한 뒤 같은 페이지를 중복 lock

- **커밋:** `f4153cf` (수정) / `ef2ce6c` (도입, 2026-09-15)
- **날짜:** 2026-09-19
- **컴포넌트:** `BTree.kt` — `findExtremeLeafPageId` (`findLeftMostLeafPageId`, `findRightMostLeafPageId`가 사용)
- **상태:** 수정 완료
- **이슈:** #41

## 증상

lower/upper bound가 없는 스캔(가장 왼쪽/오른쪽 leaf에서 시작)에서, leaf 하나에 대해 `PageLock`이 두 개 쌓였다.
같은 페이지의 핀과 read lock이 한 번씩 더 잡히는 상태였다.

## 원인

```kotlin
// 수정 전
val nextLock = storageManager.fetchPage(nextPageId, lockManager.lockMode)   // leaf여도 무조건 fetch
if (isSafeToUnlockAncestor) lockManager.releaseAncestor(currentPageLock)
lockManager.push(nextLock)
pageIdCursor = nextPageId
if (isLeaf) break
```

`isLeaf`를 확인한 뒤에도 `nextPageId`(leaf인 경우 자기 자신)를 다시 `fetchPage`하고 `push`했다. 종료 검사가 fetch보다 뒤에 있었다.

## 수정

`releaseAncestor` 호출은 그대로 두고, `if (isLeaf) break`를 재조회 코드보다 앞으로 옮겼다.

```kotlin
// 수정 후
if (isSafeToUnlockAncestor) lockManager.releaseAncestor(currentPageLock)
if (isLeaf) break
val nextLock = storageManager.fetchPage(nextPageId, lockManager.lockMode)
lockManager.push(nextLock)
pageIdCursor = nextPageId
```

## 관련

- BUG-013: 같은 `releaseAncestor`/`LockManager` 구조에서의 기준 오류
- BUG-035: latch crabbing 도입 (BTree.search에도 leaf 재fetch가 남아 있음)
- BUG-038: 같은 커밋의 `Cursor.step` 수정
