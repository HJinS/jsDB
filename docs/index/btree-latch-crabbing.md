## B-tree 인덱스 동시성 — Latch Crabbing

> [buffer-pool-manager.md](../buffer-pool/buffer-pool-manager.md)가 "버퍼 프레임 하나를 어떻게 잠그는가"를 다뤘다면, 이 문서는 한 단계 위 질문이다.
>
> - **B-tree를 루트에서 리프까지 내려가며 여러 페이지를 순회할 때, 그 페이지들의 락을 어떤 순서로 잡고 놓는가.**
> - 이 기법을 보통 Latch Crabbing(= lock coupling, hand-over-hand locking)이라고 부른다.

- InnoDB(낙관/비관 2-모드, `mtr` 배치 해제) → **[ref/MySQL/latch-crabbing.md](../ref/MySQL/latch-crabbing.md)**
- PostgreSQL(Lehman & Yao, coupling 회피) → **[ref/PostgreSQL/latch-crabbing.md](../ref/PostgreSQL/latch-crabbing.md)**
- SQLite(애초에 불필요) → **[ref/SQLite/latch-crabbing.md](../ref/SQLite/latch-crabbing.md)**

### 교과서적 정의

락을 들고 자식으로 내려간 뒤, 그 자식이 "안전(safe)"하다고 판단되면 — 즉 지금 하려는 연산(삽입/삭제)이 이 노드에서 split/merge를 유발하지 않을 것이 확실하면 — 조상들의 락을 전부 풀어버리는 방식이다. 안전하지 않으면 조상 락을 계속 쥔 채로 더 내려간다(나중에 split/merge가 위쪽까지 전파될 수도 있으니).

### 결정 - Latch Crabbing의 정석에 가까운

학습용으로 시작한 프로젝트 이다 보니, 성능적인 우선순위보다는 난이도와, 기본 언리를 공부 할 수 있는지를 기준으로 결정하였다.

[LockManager.kt](../../src/main/kotlin/index/btree/LockManager.kt), [BTree.kt](../../src/main/kotlin/index/btree/BTree.kt), [Node.kt:169-179](../../src/main/kotlin/index/btree/node/Node.kt)

```kotlin
// BTree.kt — searchLeafNode() 하향 탐색 루프 중
val nextLock = storageManager.fetchPage(nextPageId, lockManager.lockMode)   // 1. 자식 먼저 lock(coupling)
if (isSafeToUnlockAncestor) lockManager.releaseAncestor(currentLock)        // 2. 안전하면 조상 전부 해제
lockManager.push(nextLock)
```

`Node.isSafeNode()`(`Node.kt:169`)가 연산 종류별로 "안전"을 정의한다.

```kotlin
// Node.kt:169-177
fun isSafeNode(optMode: BTreeOptMode, key: ByteArray?=null, value: ByteArray?=null) = when(optMode){
    BTreeOptMode.INSERT -> keyCount < indexConfig.maxKeys && !wouldOverflow(key, value)  // 꽉 안 참
    BTreeOptMode.DELETE -> hasSurplusKey                                                 // 최소치 이상 여유
    BTreeOptMode.UPDATE -> hasSurplusKey
    BTreeOptMode.SELECT -> true                                                          // 읽기는 항상 안전
}
```

`LockManager.releaseAncestor()`(`LockManager.kt`)는 지금 쥔 락 큐에서 **가장 최근 것만 남기고 그 앞의 조상들을 순서대로(FIFO) `close()`**한다

```kotlin
// LockManager.kt
fun releaseAncestor(lock: PageLock): PageLock {
    while (lockQueue.isNotEmpty() && lockQueue.first() !== lock) lockQueue.removeFirst().close()
    return lockQueue.first()
}
```

`PageLock.downgradeLock()`은 이 흐름과는 별개로, write로 내려갔다가 실제로는 읽기만 하면 되는 것으로 판명 났을 때 write → read로 낮추는 용도다(락 해제/재획득 없이 단일 호출로 전환).

### 비교

|                   | InnoDB                                   | PostgreSQL                             | SQLite                           | jsDB                                |
| ----------------- | ---------------------------------------- | -------------------------------------- | -------------------------------- | ----------------------------------- |
| 전략              | 2-모드(낙관/비관) 배치 해제              | coupling 회피(right-link로 사후 복구)  | **불필요**(단일 스레드만 순회)   | 레벨마다 `isSafeNode()` 실시간 판단 |
| 안전성 판단 시점  | 연산 시작 전 미리(모드 선택)             | 판단 자체가 없음(detect-and-recover)   | 해당 없음                        | 매 레벨, 자식 도착 직후             |
| 실패 시           | 낙관적 실패 → 비관적으로 처음부터 재탐색 | 실패 개념 없음(항상 성공, 필요시 우회) | 해당 없음                        | 재시도 없음(하향 탐색 1회로 끝)     |
| 최대 동시 보유 락 | O(트리 높이) (비관 모드)                 | 보통 2개, ascent 시 최대 3개           | 0 (트리 전체가 커넥션 뮤텍스 뒤) | O(안전하지 않은 조상 수)            |

### 장단점

- **InnoDB식(미리 정한 낙관/비관 + 재시도)**: 대부분의 삽입/삭제가 split을 유발하지 않는다는 경험적 사실을 이용해 "일단 가볍게, 실패하면 무겁게"로 평균 비용을 낮춘다. 다만 실패 시 루트부터 재탐색하는 비용이 있고, 모드 선택 로직 자체가 복잡하다.
- **PostgreSQL식(coupling 회피)**: 락을 아예 겹쳐 쥐지 않아 데드락 가능성 자체가 구조적으로 줄고 락 보유 시간이 최소화되지만, high key/right-link라는 알고리즘적 장치를 페이지 포맷에 내장해야 해서 구현 복잡도가 가장 높다.
- **SQLite식(불필요)**: B-tree 코드가 가장 단순해지지만, 커넥션 전체가 사실상 직렬화되어 있다는 전제를 깔고 가는 것이라 진짜 병렬 B-tree 순회 자체가 불가능하다.
- **jsDB식(교과서적 crabbing)**: 구현이 직관적이고 안전성 판단이 노드 단위로 정확하지만, InnoDB의 "낙관적 우선 시도"가 주는 이점(대부분의 경우 조상 락을 아예 잡을 필요조차 없이 빠르게 통과하는 최적화)은 없다 — 매번 자식을 lock하고 나서야 안전성을 검사한다.
