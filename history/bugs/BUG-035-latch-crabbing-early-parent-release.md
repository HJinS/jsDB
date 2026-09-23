# BUG-035 Latch Crabbing 부재 — 탐색 중 부모 락을 조기 해제

- **커밋:** `7ea6698` (도입), `a421886` (API 정리)
- **날짜:** 2026-05-07 ~ 2026-05-12
- **컴포넌트:** `BTree.kt`, `LockManager.kt`(신규), `PageLock.kt`, `BufferPoolManager.kt`, `StorageManager.kt`, `Node.kt`
- **상태:** 수정 완료 (설계 문서와 일부 차이가 있음, 아래 표 참고)
- **출처:** `BTREE_IMPROVEMENT.md` §1.2, §3.2, §5.2(1), §6, §7, §8

## 증상

동시 insert/delete에서 탐색으로 찾은 경로가 split/merge를 수행하는 시점에는 이미 무효가 되었다.
다른 스레드가 그 사이에 노드를 수정하면 잘못된 페이지에 분할이 적용될 수 있었다.

## 원인

`searchLeafNode`가 노드 한 단계마다 `fetchPage(...).use { ... }`로 락과 pin을 즉시 반환했다.
자식 락을 잡기 전에 부모 락을 놓고, 수정 단계에서 다시 잡는 구조라서, 탐색과 수정 사이에 구조가 바뀔 수 있었다.
`PageLock.asReadView`/`asWriteView`도 내부에서 락을 자동으로 잡고 풀어서 호출자가 락 수명을 제어할 수 없었다. (참고: 이 시점의 traceNode 공유 문제는 BUG-026.)

## 수정

`7ea6698` (2026-05-07):

- `LockManager` 추가: 하강 경로의 `PageLock`을 큐에 누적하고, 안전한 노드에 도달하면 `releaseAncestor`로 조상을 FIFO 순서로 해제. `close()`로 잔여 락을 모두 해제.
- `PageLock`: `asReadView`/`asWriteView`의 자동 락 해제 제거. 명시적 `unlock()`과 `downgradeLock()` 추가.
- `BufferPoolManager.fetchPage(pageId, lockMode)`: 요청한 모드로 락을 **잡은 채** 반환하고, READ 요청은 디스크 I/O 후 write → read로 강등. `StorageManager`는 `.use`로 조기 해제하지 않고 `PageLock`을 그대로 전달.
- `Node.isSafeNode(optMode, key, value)`: INSERT는 `keyCount < maxKeys && !wouldOverflow`, DELETE/UPDATE는 `hasSurplusKey`, SELECT는 항상 안전.

`a421886` (2026-05-12): `LockManager` API 정리(`lockPush` → `push`), 새 페이지 생성 시 `lockManager.lockMode` 전달.

## 설계 문서와 구현의 차이

| `BTREE_IMPROVEMENT.md`                                   | 실제 구현                                                                                                |
| -------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `BTreeLatchCrab` (`lockAndPush`, `releaseAncestors`)     | `LockManager` (`push`, `releaseAncestor`, `close`)                                                       |
| `node.canAbsorb(mode)`                                   | `node.isSafeNode(optMode, key, value)`                                                                   |
| `PageLock.lockRead()`/`lockWrite()`/`unlock()`           | 락 획득은 `BufferPoolManager.fetchPage` 안에서 수행. `PageLock`에는 `unlock()`, `downgradeLock()`만 있음 |
| §7.2 헤더에 `remainingBytes` 등 저장                     | 별도 필드 없이 `FREE_SPACE_START`/`END`, `RECORD_COUNT`로 `freeSpace` 계산                               |
| §8.2 원자적 강등 (write를 쥔 채 read 획득 후 write 해제) | `writeLock().unlock()` 후 `readLock().lock()` (`BufferPoolManager.fetchPage`, `PageLock.downgradeLock`)  |

## 남은 사항

- **강등이 원자적이지 않다.** unlock과 lock 사이에 다른 writer가 들어올 수 있다.
  - 프레임은 pin되어 있어 evict되지는 않으므로 정합성 위험은 낮다고 보지만, 문서 설계와는 다르다.
- **UPDATE의 안전 조건에 공간 검사가 없다.** `isSafeNode`의 UPDATE는 `hasSurplusKey`만 보고, 값이 커져 분할이 필요한 경우는 보지 않는다 (`isSafeNode` KDoc에 "추후 용량 조건 추가 필요"라고 남아 있음).
  - 현재 `update`는 leaf에서 delete 후 `checkOverflowAndSplitFirst`를 호출하며, 조상 락이 이미 풀렸을 때는 `b4824a8`의 refetch 로직에 의존한다. 이 조합이 안전한지는 별도로 검증하지 않았다.
- **leaf 중복 fetch.** `BTree.search`는 `searchLeafNode`가 이미 leaf 락을 큐에 넣은 뒤 같은 leaf를 다시 `fetchPage`해서 push한다 (핀·락이 한 번 더 잡힘).

## 관련

- BUG-013: 이 구조에서 `releaseAncestor` 기준이 잘못되어 safe node 자신의 락이 풀린 문제
- BUG-017: `BufferPoolManager`의 락 취득 순서
- BUG-023: `BufferPoolManager` pinCount/pageId 동시성
- BUG-026: `traceNode` 공유 문제
