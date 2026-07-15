# BUG-013 Lock Crabbing — releaseAncestor 기준 오류

- **커밋:** `e8d8cd1`
- **날짜:** 2026-06-24
- **컴포넌트:** `BTree.kt`, `LockManager.kt`
- **상태:** 수정 완료

## 증상

3000개 삽입 테스트에서 간헐적으로 `NoSuchElementException: List is empty` 또는 `No more data` 오류 발생. 재현율 약 50%.

## 원인

`searchLeafNode`에서 safe node 도달 시 `lockManager.realeaseAncester(nextLock)`으로 조상 lock을 해제. 이때 `nextLock`은 아직 `push` 되기 전 상태이므로, 큐에서 `nextLock` 이전까지 해제하면 safe node 자신의 lock이 해제됨.

결과: safe node의 frame이 evict 대상이 되어 다른 페이지가 그 frame을 점유 → split 과정에서 해당 frame을 읽으면 빈 splitKeys → `NoSuchElementException`.

```kotlin
// 수정 전 (버그)
lockManager.push(nextLock)
lockManager.realeaseAncester(nextLock)  // nextLock 기준 → safe node 자신도 해제됨

// 수정 후
lockManager.realeaseAncester(currentLock)  // push 이전, currentLock 기준으로 호출
lockManager.push(nextLock)
```

## 수정

- `realeaseAncester` 호출을 `nextLock.push` **이전**으로 이동, 기준을 `currentLock`으로 변경.
- `LockManager.realeaseAncester`: 큐 앞에서부터 순회하다 기준 lock이 나오면 중단 (exception throw 제거).
- 영향 범위: `searchLeafNode`, `traverse`, `findLeftMostLeafPageId` 세 곳 모두 동일하게 수정.
