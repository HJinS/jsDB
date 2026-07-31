## LRU (Buffer Replacement Policy)

> `BufferPoolManager`에 빈 프레임이 없을 때, 어떤 프레임을 victim으로 골라 evict 할지 결정하는 정책이다.
> 이 프로젝트는 클래스 이름 그대로 `MidpointLRUPolicy` — InnoDB의 Midpoint Insertion 전략을 채택했다.
> Lock/Pin 등 `BufferPoolManager` 자체의 동시성 제어는 [buffer-pool-manager.md](./buffer-pool-manager.md)에서 별도로 다룬다.

### MySQL(InnoDB) — Midpoint Insertion

**[MySQL BufferPool](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L135)**
- 여기에는 BufferPool에 관련된 전체적인 설명이 적혀있다. 다만 여기에 LRU 관련 세부적인 내용은 적혀있지 않다.
- `Pointer-Swizzling`이라는 기법이 나오는데, 복잡도가 너무 증가하여 해당 프로젝트에서는 사용하지 않았다.

**[MySQL BufferPool LRU 구현](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0lru.cc#L61)**
- 여기에는 MySQL의 BufferPool LRU 관련 구현이 있으나 이것만으로는 전체적인 MySQL LRU의 동작 과정을 파악하기 어려워 다음의 문서를 참고했다.
- [MySQL BufferPool Scan Resistant](https://dev.mysql.com/doc/refman/9.7/en/innodb-performance-midpoint_insertion.html)
  - BufferPool의 scan 저항에 관련하여 적혀있다. 주로 innodb_old_blocks_pct와 innodb_old_blocks_time 에 대해서 다루고 있다.
- [MySQL BufferPool](https://dev.mysql.com/doc/refman/9.1/en/innodb-buffer-pool.html)
  - MySQL의 LRU - Midpoint Insertion에 대해서 이야기 하고 있다.
  - Midpoint Insertion이란 기본적으로 young(5/8), old(3/8) 의 비율을 가지고, 페이지를 읽을 경우 Midpoint(Old List의 가장 앞) 에 삽입하는것을 의미한다.
  - Old 영역에 있는 페이지를 처음 접근 한 경우 그 페이지를 Young 여역으로 옮긴다.
    - 이는 read-ahead 오퍼레이션이나 table-scan 같은 오퍼레이션이 기존의 캐싱에 영향을 끼치지 않도록 한다.
- 자세한 코드는 이 부분을 보면 알 수 있다.
  - [midpoint insert 코드](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0lru.cc#L862)
  - 특이점은 처음에는 1개의 리스트로 시작(무조건 young 취급) 하다가 특정 길이에 도달 할 경우 old Sublist를 만든다는 점이다.

```cpp
static inline void buf_LRU_add_block_low(buf_page_t *bpage, bool old) {
  ...
  if (!old || (UT_LIST_GET_LEN(buf_pool->LRU) < BUF_LRU_OLD_MIN_LEN)) {
    UT_LIST_ADD_FIRST(buf_pool->LRU, bpage);

    bpage->freed_page_clock = buf_pool->freed_page_clock;
  } else {
#ifdef UNIV_LRU_DEBUG
    /* buf_pool->LRU_old must be the first item in the LRU list
    /* midpoint insert 부분*/
    whose "old" flag is set. */
    ut_a(buf_pool->LRU_old->old);
    ut_a(!UT_LIST_GET_PREV(LRU, buf_pool->LRU_old) ||
         !UT_LIST_GET_PREV(LRU, buf_pool->LRU_old)->old);
    ut_a(!UT_LIST_GET_NEXT(LRU, buf_pool->LRU_old) ||
         UT_LIST_GET_NEXT(LRU, buf_pool->LRU_old)->old);
#endif /* UNIV_LRU_DEBUG */
    UT_LIST_INSERT_AFTER(buf_pool->LRU, buf_pool->LRU_old, bpage);

    buf_pool->LRU_old_len++;
  }
  ...
  /* midpoint insert 후에 old 플래그 설정 및 필요시 비율 조정 */
  if (UT_LIST_GET_LEN(buf_pool->LRU) > BUF_LRU_OLD_MIN_LEN) {
    ut_ad(buf_pool->LRU_old);

    /* Adjust the length of the old block list if necessary */

    buf_page_set_old(bpage, old);
    buf_LRU_old_adjust_len(buf_pool);

  } else if (UT_LIST_GET_LEN(buf_pool->LRU) == BUF_LRU_OLD_MIN_LEN) {
    /* The LRU list is now long enough for LRU_old to become
    defined: init it */

    buf_LRU_old_init(buf_pool); // Midpoint 초기화
  } else {
    buf_page_set_old(bpage, buf_pool->LRU_old != nullptr);
  }
}
```

**핵심 포인트**
- `LRU_old`가 old/young 서브리스트의 경계(midpoint)를 가리키는 포인터다.
- 새로 읽은 페이지는 `LRU_old` 바로 뒤(=old 영역의 head)에 삽입된다. 리스트 맨 앞(hot)을 곧바로 차지하지 못한다.
- old 영역의 페이지가 `innodb_old_blocks_time`(ms) 이상 지난 뒤 재접근되면 young으로 승격된다. 너무 빨리 재접근되면(같은 read-ahead/스캔 안에서) 승격시키지 않아 스캔 저항(scan resistance)을 갖는다.
- `buf_LRU_old_adjust_len()`이 블록 추가/제거마다 `LRU_old` 위치를 `innodb_old_blocks_pct`(기본 37%) ± tolerance 안으로 재조정한다.
- pin(버퍼 고정, buffer-fix count > 0)된 페이지도 **리스트에는 그대로 남아있고**, evict 스캔 시점에 건너뛴다. 리스트에서 물리적으로 빠지지 않는다.

### PostgreSQL — Clock-Sweep

> Lock/Pin 규칙 자체는 [buffer-pool-manager.md](./buffer-pool-manager.md)에 정리되어 있다. 여기서는 victim 선택 알고리즘만 다룬다.

각 버퍼는 `usage counter`를 가지고, pin 될 때마다(상한까지) 증가한다. `nextVictimBuffer`(시계 바늘)가 전체 버퍼를 원형으로 순환하며 victim을 찾는다.

1. `buffer_strategy_lock` 획득
2. `nextVictimBuffer`가 가리키는 버퍼 선택, 바늘 전진 → `buffer_strategy_lock` 릴리즈
3. 선택한 버퍼가 pin 되었거나 usage count > 0이면 사용 불가 → usage count 감소 후 `buffer_strategy_lock` 재획득하여 다음 버퍼로 (스텝 2부터 반복)
4. 사용 가능하면 버퍼 pin 후 반환

**핵심 포인트**
- InnoDB처럼 리스트를 재배치하지 않는다. 원형 포인터 이동 + usage count 감쇠만으로 근사 LRU 효과를 낸다.
- 스캔 저항은 별도의 `Buffer Ring Replacement`(VACUUM/시퀀셜 스캔/대량 쓰기 전용 소형 링 버퍼)로 달성한다. InnoDB처럼 하나의 리스트 안에서 old/young을 나누는 방식이 아니라, **아예 별도의 작은 버퍼 풀을 분리**하는 방식이다.

### SQLite — Pure LRU (`pcache1.c`)

기본 페이지 캐시 구현인 [`pcache1.c`](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c)는 InnoDB/PostgreSQL과 달리 세그먼트나 usage counter 없이 **순수 LRU**(단일 원형 이중연결리스트)를 사용한다.

- `PgHdr1`(`pcache1.c:117`)이 `pLruNext`/`pLruPrev`로 LRU 리스트를 구성한다.
- pinned 여부는 별도 플래그가 아니라 **리스트 멤버십 자체**로 판별한다.
  ```c
  // pcache1.c:133-134
  #define PAGE_IS_PINNED(p)    ((p)->pLruNext==0)
  #define PAGE_IS_UNPINNED(p)  ((p)->pLruNext!=0)
  ```
- `pcache1PinPage()`(`pcache1.c:578`)는 페이지를 pin할 때 LRU 리스트에서 **물리적으로 제거**한다(`pLruNext = 0`).
  ```c
  // pcache1.c:583-586
  pPage->pLruPrev->pLruNext = pPage->pLruNext;
  pPage->pLruNext->pLruPrev = pPage->pLruPrev;
  pPage->pLruNext = 0;
  ```
- `pcache1Unpin()`(`pcache1.c:1078`)은 unpin 시 리스트 head(`pGroup->lru` 바로 뒤)에 다시 삽입한다.
- victim 선택은 `pcache1FetchStage2()`(`pcache1.c:874`) 안에서 `pGroup->lru.pLruPrev`, 즉 **리스트 tail(가장 오래 unpinned 상태였던 페이지)** 을 그대로 재사용한다 — old/young 구분도, 재접근 시간 체크도 없다.
- 락 구조도 가장 단순하다. 기본 설정에서는 프로세스 전체에서 `PGroup` 하나(`static PGroup`)를 공유하고, 이 그룹 전체를 `pGroup->mutex` **단일 뮤텍스**(`MUTEX_STATIC_LRU`)로 보호한다(`pcache1EnterMutex`/`pcache1LeaveMutex`). InnoDB처럼 LRU/free-list/flush를 분리하지도, PostgreSQL처럼 해시 파티션을 나누지도 않는다.

### jsDB 구현 — `MidpointLRUPolicy`

[MidpointLRUPolicy.kt](../../src/main/kotlin/storageEngine/lru/MidpointLRUPolicy.kt), [GenerationalList.kt](../../src/main/kotlin/storageEngine/lru/GenerationalList.kt), [PromotionRule.kt](../../src/main/kotlin/storageEngine/lru/PromotionRule.kt), [DoublyLinkedList.kt](../../src/main/kotlin/storageEngine/lru/DoublyLinkedList.kt)

구조는 InnoDB를 그대로 참고했다.

- `HashMap<Int, LRUNode>`(`MidpointLRUPolicy.kt:10`) + 이중연결리스트(`DoublyLinkedList`) → InnoDB의 buffer-page hash table + `LRU` list 구조와 대응.
- `GenerationalList`의 `midPoint: LRUNode?`(`GenerationalList.kt:16`)가 InnoDB의 `LRU_old` 포인터와 동일한 역할을 한다.
- 새 페이지 삽입은 `addOld()`(`GenerationalList.kt:60`)에서 `midPoint` 바로 앞에 삽입한다 → InnoDB의 `UT_LIST_INSERT_AFTER(buf_pool->LRU, buf_pool->LRU_old, bpage)`와 동일한 미드포인트 삽입.
- `PromotionRule.isPromotable()`(`PromotionRule.kt:9`)이 `lastAccessTime` 경과 시간을 `lruOldBlocksTimeMs`와 비교해 승격 여부를 결정한다 → `innodb_old_blocks_time`과 동일한 역할(read-ahead/풀스캔 캐시 오염 방지).
- `adjustRatio()`(`GenerationalList.kt:87`)가 young 개수가 `youngRatio * capacity`(`maxYoungCount`)를 넘으면 `midPoint`를 뒤로 밀어 비율을 유지한다 → `buf_LRU_old_adjust_len()`과 동일한 역할.
- eviction은 `removeOldest()`(`GenerationalList.kt:72`)가 리스트 tail(`linkedList.getLast()`)에서 골라온다.

**InnoDB와의 차이 — pin 처리 방식**

InnoDB는 buffer-fixed(pinned) 페이지도 LRU 리스트 안에 그대로 두고 evict 스캔 시 건너뛰지만, jsDB는 `pin()`(`MidpointLRUPolicy.kt:64`)에서 `generationalList.remove(node)` + `node.resetLink()`로 리스트에서 **물리적으로 제거**했다가, `unpin()`(`MidpointLRUPolicy.kt:50`)에서 원래 있던 영역(old/young)에 맞춰 다시 삽입한다.

```kotlin
// MidpointLRUPolicy.kt:64-76
override fun pin(frameId: Int) {
    val node = map[frameId]
    if(node == null){
        map[frameId] = LRUNode(frameId).apply {
            isPinned = true
            isOld = true
        }
    } else if(!node.isPinned){
        generationalList.remove(node)
        node.resetLink()
        node.isPinned = true
    }
}
```

이 방식은 사실 InnoDB보다는 **SQLite `pcache1.c`의 pin/unpin 패턴**(`PAGE_IS_PINNED`가 리스트 멤버십으로 결정되고, pin 시 물리적으로 unlink)에 더 가깝다. 다만 SQLite는 old/young 구분이 아예 없는 순수 LRU이므로, jsDB는 "SQLite식 pin 처리 + InnoDB식 Midpoint Insertion"을 조합한 형태에 가깝다.

### 비교

| 항목 | InnoDB | PostgreSQL | SQLite | jsDB |
|---|---|---|---|---|
| 알고리즘 | Midpoint Insertion (segmented LRU) | Clock-sweep (usage counter) | 순수 LRU (단일 리스트) | Midpoint Insertion (segmented LRU) |
| 자료구조 | 이중연결리스트 + `LRU_old` 포인터 | 원형 배열 + `nextVictimBuffer` | 원형 이중연결리스트 | 이중연결리스트 + `midPoint` 포인터 |
| 스캔 저항 | old/young 서브리스트 + 재접근 시간(`old_blocks_time`) 체크 | 별도의 소형 buffer ring 분리 | 없음 | old/young 서브리스트 + 재접근 시간(`lruOldBlocksTimeMs`) 체크 |
| 승격 조건 | old 영역에서 시간 조건 만족 후 재접근 시 young으로 이동 | pin될 때마다 usage counter 증가 | 없음(항상 head로 이동) | old 영역에서 시간 조건 만족 후 재접근 시 young으로 이동 |
| pinned 페이지의 리스트 위치 | 리스트에 남아있음, 스캔 시 skip | 배열에 남아있음, usage count/pin 여부로 skip | 리스트에서 물리적으로 제거 | 리스트에서 물리적으로 제거 |

### 이 프로젝트에서는

- **InnoDB의 Midpoint Insertion**을 채택했다. Clock-sweep(PostgreSQL) 대비 구현이 직관적이고, 순수 LRU(SQLite) 대비 read-ahead/풀스캔에 의한 캐시 오염을 막을 수 있기 때문이다.
- pin된 프레임을 리스트에서 물리적으로 제거하는 방식은 SQLite의 `PAGE_IS_PINNED`/`pcache1PinPage()` 패턴을 참고했다. Evict 후보를 고를 때 pinned 여부를 매번 skip 처리할 필요가 없어 `removeOldest()` 구현이 단순해진다는 장점이 있다. 대신 pin/unpin이 호출될 때마다 리스트 연산이 추가로 발생한다.

추가 결정 사항
- `youngRatio`, `capacity`, `lruOldBlocksTimeMs`는 `MidpointLruConfig`로 외부에서 주입.
