## BufferPoolManager
> `DiskManager` 에서 `FileChannel`을 통해 읽은 데이터를 담아두는 곳을 `Frame`이라고 칭한다.
> `Frame`에서는 `ByteBuffer.allocateDirect`을 통해 데이터를 담아둔다.
> 이 `Frame`의 풀(pool)을 관리하는 역할을 이 `BufferPoolManager`가 하는 할이다.

> evict victim을 고르는 LRU 정책 자체는 [lru.md](./lru.md)에서 별도로 다룬다. 이 문서는 락(latch)/pin 등 `BufferPoolManager`의 동시성 제어에 집중한다.

MySQL, PostgreSQL, sqlite가 buffer pool을 동시성 환경에서 어떻게 보호하는지 정리하면 다음과 같다.

### MySQL(InnoDB) — 뮤텍스 분리

**[buf0buf.cc 개요 주석](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L134)** 에 버퍼풀을 보호하는 뮤텍스 목록이 정리되어 있다.

| 뮤텍스 | 보호 대상 |
|---|---|
| `chunks_mutex` | chunks, n_chunks(리사이징 중), madvise 상태 |
| `LRU_list_mutex` | LRU_list |
| `free_list_mutex` | free_list, withdraw list |
| `flush_state_mutex` | flush 상태 관련 자료구조 |
| `zip_free` | zip_free 배열 |
| `zip_hash` | zip_hash 해시, in_zip_hash 플래그 |

- 버퍼 프레임(페이지 내용) 자체는 이 뮤텍스들과 별개로, 제어 블록마다 있는 **읽기-쓰기 락**(`block->lock`)으로 보호한다.
- 즉 "메타데이터(LRU/free-list/flush 등)를 보호하는 여러 개의 짧은 뮤텍스" + "페이지 내용을 보호하는 프레임별 rw-lock" 두 계층으로 나뉘어 있다.
  - [The bufferfix operation does not lock the contents of the frame, however. For this purpose, the control block contains a read-write lock.](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L171-L172))

### PostgreSQL — 파티션 락 + 3단계 Content Lock

#### lock 구조
Pin: 버퍼를 건드리기 전에 무조건 `Pin`을 잡아야함
Content Lock: `shared`, `share-exclusive`, `exclusive` 3종류가 있으며, 이건 짧게만 들고 있어야함.
[PostgreSQL Lock](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/README#L43)

1. 튜플을 스캔하려면 pin + 최소 share lock 필요
2. 튜플이 유효하다고 판단되면 content lock은 풀어도 되지만 pin은 유지한 채로 데이터 접근 가능
3. 튜플 추가나 xmin/xmax 변경 시 pin + exclusive lock 필요
4. hint bit 같은 비핵심 정보는 share-exclusive lock만으로도 수정 가능
5. 튜플 물리적 삭제나 페이지 공간 압축(compact)에는 pin + exclusive lock, 그리고 ref-count가 1(자기 자신만 핀 보유)임을 확인해야 함 - LockBufferForCleanup()이 이를 담당
6. 버퍼를 디스크에 쓸 때는 share-exclusive lock 필요

**메타데이터 락도 InnoDB처럼 분리되어 있다.**
- [There is a system-wide LWLock, the BufMappingLock, that notionally protects the mapping from buffer tags (page identifiers) to buffers.](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/README#L123)
- `BufMappingLock` — 버퍼 태그(page id) → 버퍼 매핑을 보호하는 해시 락.
- `buffer_strategy_lock` — clock-sweep의 `nextVictimBuffer` 이동을 보호하는 별도의 전역 스핀락. victim 탐색(LRU 정책, [lru.md](./lru.md) 참고) 전용이라 매핑 조회와 락 경합이 없다.
- 버퍼 헤더별 스핀락 — 헤더의 usage counter/refcount 갱신 처럼, 헤더의 필드를 변경할 경우 사용하는 짧은 락

#### 특징
> 흥미로운 점은 과거(PG_16)에 content-lock을 LWLock(LightWeight Lock)으로 구현 되어 있다.
>  -  실제 share_exclusive 에 대한 명시적 상수 같은 것도 없고, 코드상 동작 분기를 통해 관리 된 것으로 보인다. 

[PG 16 버전의 Buffer Description](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/include/storage/buf_internals.h#L244)
```c
typedef struct BufferDesc
{
	BufferTag	tag;			/* ID of page contained in buffer */
	int			buf_id;			/* buffer's index number (from 0) */

	/* state of the tag, containing flags, refcount and usagecount */
	pg_atomic_uint32 state;

	int			wait_backend_pgprocno;	/* backend of pin-count waiter */
	int			freeNext;		/* link in freelist chain */
	LWLock		content_lock;	/* to lock access to buffer contents */
} BufferDesc;
```

[최신 버전의 Buffer Description.
명시적인 Lock이 사라지고 state에 패킹 되어 있다.](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/include/storage/buf_internals.h#L305)

> We used to use an LWLock to implement the content lock,
> but having a dedicated implementation of content locks allows us to implement some otherwise hard things
> (e.g. race-freely checking if AIO is in progress before locking a buffer exclusively

```c

/* 변경된 BufferDesc 구조 */
typedef struct BufferDesc
{
	BufferTag	tag;
	int			buf_id;
	pg_atomic_uint64 state;
	int			wait_backend_pgprocno;
	PgAioWaitRef io_wref;		/* set iff AIO is in progress */
	proclist_head lock_waiters;
} BufferDesc;

/* 명시적으로 Lock 상수가 정의 되어 있음(1개 늘어남) */
typedef enum BufferLockMode
{
	BUFFER_LOCK_UNLOCK,
	BUFFER_LOCK_SHARE,
	BUFFER_LOCK_SHARE_EXCLUSIVE,
	BUFFER_LOCK_EXCLUSIVE,
} BufferLockMode;

/* Lock 획득을 시도하고 PGSemaphoreLock 을 사용하여 잠에 드는 등의 로직 */
static inline void BufferLockAcquire(Buffer buffer, BufferDesc *buf_hdr, BufferLockMode mode)
{
	PrivateRefCountEntry *entry;
	int			extraWaits = 0;
	entry = GetPrivateRefCountEntry(buffer, true);
	Assert(entry->data.lockmode == BUFFER_LOCK_UNLOCK);
	HOLD_INTERRUPTS();
	for (;;)
	{
		uint32		wait_event = 0; /* initialized to avoid compiler warning */
		bool		mustwait;
		mustwait = BufferLockAttempt(buf_hdr, mode);
		if (likely(!mustwait))
		{
			break;
		}
		BufferLockQueueSelf(buf_hdr, mode);
		mustwait = BufferLockAttempt(buf_hdr, mode);
		if (!mustwait)
		{
			BufferLockDequeueSelf(buf_hdr);
			break;
		}
		switch (mode)
		{
			case BUFFER_LOCK_EXCLUSIVE:
				wait_event = WAIT_EVENT_BUFFER_EXCLUSIVE;
				break;
			case BUFFER_LOCK_SHARE_EXCLUSIVE:
				wait_event = WAIT_EVENT_BUFFER_SHARE_EXCLUSIVE;
				break;
			case BUFFER_LOCK_SHARE:
				wait_event = WAIT_EVENT_BUFFER_SHARED;
				break;
			case BUFFER_LOCK_UNLOCK:
				pg_unreachable();

		}
		pgstat_report_wait_start(wait_event);
		for (;;)
		{
			PGSemaphoreLock(MyProc->sem);
			if (MyProc->lwWaiting == LW_WS_NOT_WAITING)
				break;
			extraWaits++;
		}
		pgstat_report_wait_end();
		pg_atomic_fetch_and_u64(&buf_hdr->state, ~BM_LOCK_WAKE_IN_PROGRESS);
	}
	entry->data.lockmode = mode;

	while (unlikely(extraWaits-- > 0))
		PGSemaphoreUnlock(MyProc->sem);
}

/* 실제 Lock 구현 부분 */
static inline bool
BufferLockAttempt(BufferDesc *buf_hdr, BufferLockMode mode)
{
	uint64		old_state;
	old_state = pg_atomic_read_u64(&buf_hdr->state);
	while (true)
	{
		uint64		desired_state;
		bool		lock_free;
		desired_state = old_state;
		if (mode == BUFFER_LOCK_EXCLUSIVE)
		{
			lock_free = (old_state & BM_LOCK_MASK) == 0;
			if (lock_free)
				desired_state += BM_LOCK_VAL_EXCLUSIVE;
		}
		else if (mode == BUFFER_LOCK_SHARE_EXCLUSIVE)
		{
			lock_free = (old_state & (BM_LOCK_VAL_EXCLUSIVE | BM_LOCK_VAL_SHARE_EXCLUSIVE)) == 0;
			if (lock_free)
				desired_state += BM_LOCK_VAL_SHARE_EXCLUSIVE;
		}
		else
		{
			lock_free = (old_state & BM_LOCK_VAL_EXCLUSIVE) == 0;
			if (lock_free)
				desired_state += BM_LOCK_VAL_SHARED;
		}
		if (likely(pg_atomic_compare_exchange_u64(&buf_hdr->state,
												  &old_state, desired_state)))
		{
			if (lock_free)
			{
				return false;
			}
			else
				return true;	/* somebody else has the lock */
		}
	}
	pg_unreachable();
}
```
[실제 최신 버전 Lock 구현](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/bufmgr.c#L5921)
- 위의 코드에는 실제 Lock이 구현 된 부분의 코드 일부를 적어 놓았다.(길이를 줄이기 위해 주석은 제거)
- github링크를 따라 들어가보면 알겠지만, 기본적인 Lock 구조는 이전 버전에서 사용하던 LWLock의 동작방식을 대부분 그대로 가져간다.
  1. 1차 시도: BufferLockAttempt(buf_hdr, mode) — CAS로 바로 잡아본다. 성공하면 끝(likely(!mustwait) — 대부분 여기서 끝나길 기대). 
  2. 실패하면 큐에 등록: BufferLockQueueSelf(buf_hdr, mode) — 대기자 목록(BufferDesc.lock_waiters, proclist_head)에 자신을 넣는다. 
  3. 2차 시도: 큐에 넣은 다음에 다시 한 번 CAS를 시도한다.
     1. 1차 시도와 큐 등록 사이에 락이 풀렸을 수도 있으니, "큐에 등록된 걸 다른 락 해제자가 보장적으로 볼 수 있는 상태"에서 한 번 더 확인.
     2. 여기서 성공하면 큐 등록을 취소(BufferLockDequeueSelf)하고 끝. 
  4. 그래도 실패하면 진짜로 잠든다.
     1. `PGSemaphoreLock`을 사용하여 lock 획득을 기다린다.
     2. 최종적으로 lock자체는 위의 state를 가지고 CAS(Compare And Swap)를 통해 이루어진다.
     3. [아래 코드](https://github.com/postgres/postgres/blob/60826a352d497b15a30de29b7796d0c2d097a0e3/src/backend/port/atomics.c#L34)를 보면 네이티브 64비트 CAS 명령어를 지원하지 않을 경우에 대비한 구현이 있다.

> 코드를 읽어보면 몇가지 흥미로운 점을 알 수 있다.
> 기본적으로 state의 bitmask 연산을 통해 다음과 같은 정보를 얻을 수 있다.
> - 다른 누가 exclusive lock(share-exclusive, exclusive)을 잡았는지 여부
> - share-lock을 잡은 개수
> 그러나 이 연산을 통해 lock을 획득할 수 없음을 알아도 코드를 보면 CAS 연산의 **메모리 베리어** 효과를 얻기 위해 의도적으로 같은 **desired** 값을 가지고 CAS 연산을 수행한다.

```c
pg_atomic_compare_exchange_u64_impl(volatile pg_atomic_uint64 *ptr, uint64 *expected, uint64 newval)
{
	bool ret;

	/*
	 * Do atomic op under a spinlock. It might look like we could just skip
	 * the cmpxchg if the lock isn't available, but that'd just emulate a
	 * 'weak' compare and swap. I.e. one that allows spurious failures. Since
	 * several algorithms rely on a strong variant and that is efficiently
	 * implementable on most major architectures let's emulate it here as
	 * well.
	 */
	SpinLockAcquire((slock_t *) &ptr->sema);

	/* perform compare/exchange logic */
	ret = ptr->value == *expected;
	*expected = ptr->value;
	if (ret)
		ptr->value = newval;

	/* and release lock */
	SpinLockRelease((slock_t *) &ptr->sema);

	return ret;
}
```

즉 PostgreSQL은 "1. 파티션 분할된 매핑 락 2. victim 탐색 전용 전역 락 3. 페이지 내용 보호용 3단계 content lock"으로 InnoDB보다 더 잘게 나뉘어 있다.

### sqlite(pcache1.c) — 단일 그룹 뮤텍스

기본 설정에서는 프로세스 전체가 `PGroup` 하나(`static PGroup`, [`pcache1.c:225`](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L225))를 공유하고, LRU 리스트/해시 테이블 전체를 `pGroup->mutex` **단일 뮤텍스**(`MUTEX_STATIC_LRU`)로 보호한다(`pcache1EnterMutex`/`pcache1LeaveMutex`, [`pcache1.c:253`](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L253)).

- InnoDB처럼 LRU/free-list/flush를 분리하지도, PostgreSQL처럼 해시를 파티션으로 나누지도 않는다.
- SQLite는 페이지 단위 rw-lock 대신 데이터베이스 파일/WAL 레벨 락으로 동시성을 제어하기 때문에, 버퍼(페이지 캐시) 계층에서는 "전체를 보호하는 뮤텍스 하나"면 충분하다는 설계다.
- 자세한 LRU 리스트 pin/unpin 메커니즘은 [lru.md](./lru.md)의 SQLite 절 참고.

### jsDB 구현 — `globalLatch` + Frame별 `ReentrantReadWriteLock`

[BufferPoolManager.kt](../../src/main/kotlin/storageEngine/BufferPoolManager.kt), [Frame.kt](../../src/main/kotlin/storageEngine/page/Frame.kt), [PageLock.kt](../../src/main/kotlin/storageEngine/page/PageLock.kt)

두 계층으로 나뉜다.

- **메타데이터 락**: `globalLatch: ReentrantLock`(`BufferPoolManager.kt:53`) 하나가 `pageTable`, `freeList`, `replacer`(LRU) 전체를 보호한다. InnoDB(뮤텍스 분리)나 PostgreSQL(파티션 락 + 전용 strategy 락)처럼 세분화되어 있지 않고, sqlite의 단일 `PGroup` 뮤텍스와 개념적으로 가장 가깝다.
- **콘텐츠 락**: `Frame.latch: ReentrantReadWriteLock`(`Frame.kt:15`)가 프레임별로 하나씩 있어 페이지 데이터를 보호한다. read/write 2단계뿐이며, PostgreSQL의 share-exclusive(힌트비트 갱신용) 같은 중간 단계는 없다. 대신 `PageLock.downgradeLock()`(`PageLock.kt:56`)으로 write → read 다운그레이드만 지원한다.
- **pin**: `Frame.pinCount: AtomicInteger`(`Frame.kt:16`)가 refcount 역할을 한다. `unpinPage()`에서 카운트가 0이 되는 순간에만 `replacer.unpin(frameId)`를 호출해 LRU 리스트로 되돌린다(`BufferPoolManager.kt:190-205`). InnoDB의 buffer-fix count, PostgreSQL의 pin refcount와 같은 개념이다.

**락 취득 순서** (`fetchPage()`, `BufferPoolManager.kt:66-138`)
1. `globalLatch` 획득 → pageTable 조회/갱신, `replacer.pin()`, `pinCount` 갱신까지만 짧게 수행하고 해제.
2. (필요시) `frame.latch.writeLock()`을 잡은 채로 디스크 I/O 수행 — 이 구간은 `globalLatch` 밖에서 진행되어 다른 페이지에 대한 `fetchPage`/`unpinPage`를 막지 않는다.
3. `lockMode`에 따라 read/write 락을 최종적으로 잡고 `PageLock`을 반환.

이 "짧은 전역 메타락 → 해제 → 긴 프레임락" 순서는 InnoDB(LRU_list_mutex는 짧게, `block->lock`은 IO 동안 길게)와 PostgreSQL(BufMappingLock은 짧게, content lock은 IO 동안 길게) 모두가 따르는 패턴과 같은 철학이다.

### 락 세분화 비교

| 항목 | InnoDB | PostgreSQL | sqlite | jsDB |
|---|---|---|---|---|
| 메타데이터 락 | 뮤텍스 6종 분리(LRU/free/flush/zip 등) | 파티션 분할 `BufMappingLock` + 전용 `buffer_strategy_lock` | 단일 `PGroup` 뮤텍스 | 단일 `globalLatch` |
| 콘텐츠 락 | 프레임별 rw-lock(`block->lock`) | 프레임별 3단계 content lock(shared/share-exclusive/exclusive) | 없음(파일/WAL 레벨 락으로 대체) | 프레임별 `ReentrantReadWriteLock`(read/write 2단계) |
| pin(refcount) | buffer-fix count | pin refcount | 리스트 멤버십(`pLruNext==0`)으로 pinned 판별 | `AtomicInteger` pinCount |
| 확장성 특성 | 세분화된 뮤텍스로 컨텐션 분산 | 파티션 + 전용 strategy 락으로 컨텐션 분산 | 단일 락 — 대신 페이지 단위 동시성 자체를 요구하지 않는 설계 | 단일 락 — 메타데이터 갱신이 짧아 실사용 부담은 낮지만 스케일 아웃 여지는 낮음 |

### 설계 결정 및 트레이드오프

- **단일 `globalLatch`를 선택한 이유**: 파티션 분할이나 뮤텍스 세분화는 정합성 버그를 만들기 쉽고, 이 프로젝트 규모에서는 컨텐션보다 정합성이 우선이었다. sqlite의 단일 `PGroup` 뮤텍스 설계와 같은 절충이다.
- **`deletePage`가 `globalLatch`를 쥔 채로 `frame.latch`까지 잡는 것은 의도적 설계**다 ([BUG-017](../../history/bugs/BUG-017-bufferpool-lock-order-deadlock.md)). `globalLatch`를 먼저 해제하고 `frame.latch`를 잡으면, 그 사이 다른 스레드가 `replacer.evict()`로 같은 frame을 재사용해버리는 race window가 생긴다. `PageLock.close()`가 `frame.latch`를 먼저 해제하고 `globalLatch`를 나중에 잡는 순서를 지키는 한(현재 코드 전부가 그러함) 데드락은 발생하지 않지만, 향후 `asWriteView`/`asReadView` 블록 안에서 BPM 메서드를 직접 호출하는 코드가 추가되면 사이클이 생길 수 있어 주의가 필요하다.
- **pin/pageId 갱신은 반드시 같은 락 스코프 안에서** ([BUG-023](../../history/bugs/BUG-023-bufferpool-fetchpage-pincount-concurrency.md)). 초기 구현에서는 `pinCount`를 값으로 덮어써서 동시 fetch 시 카운트가 유실되고, `frame.pageId` 설정이 `reset()`보다 먼저 이뤄지지 않아 다른 스레드가 이전 페이지 내용을 새 pageId로 읽는 문제가 있었다. `pinCount`를 `AtomicInteger` increment로, `frame.pageId`를 `AtomicLong`으로 바꾸고 "`writeLock` 안에서 pageId 설정 → `reset()` → I/O" 순서를 강제해 수정했다. InnoDB/PostgreSQL이 원래부터 강제하는 "pin count와 page identity 변경은 반드시 같은 락 아래"라는 불변식을 뒤늦게 맞춘 사례다.
