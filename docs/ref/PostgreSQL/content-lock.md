## PostgreSQL Buffer Content Lock

> [buffer-pool-manager.md](../../buffer-pool/buffer-pool-manager.md)의 PostgreSQL 절 요약을 더 깊게 파고든 문서. content lock이 버전에 따라 실제로 다르게 구현되어 있다는 걸 소스로 확인한 내용을 정리한다.
> hint bit는 [page.md](./page.md)에서 다룬다 — 이 문서의 SHARE_EXCLUSIVE 동기와 직접 연결된다.

### lock 구조 — 버퍼 접근 6가지 규칙

Pin: 버퍼를 건드리기 전에 무조건 `Pin`을 잡아야 함.
Content Lock: `shared`, `share-exclusive`, `exclusive` 3종류가 있으며, 이건 짧게만 들고 있어야 함.
[PostgreSQL README](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/README#L43)

1. 튜플을 스캔하려면 pin + 최소 share lock 필요
2. 튜플이 유효하다고 판단되면 content lock은 풀어도 되지만 pin은 유지한 채로 데이터 접근 가능
3. 튜플 추가나 xmin/xmax 변경 시 pin + exclusive lock 필요
4. hint bit 같은 비핵심 정보는 share-exclusive lock만으로도 수정 가능
5. 튜플 물리적 삭제나 페이지 공간 압축(compact)에는 pin + exclusive lock, 그리고 ref-count가 1(자기 자신만 핀 보유)임을 확인해야 함 - `LockBufferForCleanup()`이 이를 담당
6. 버퍼를 디스크에 쓸 때는 share-exclusive lock 필요

### 메타데이터 락 — InnoDB처럼 분리되어 있다

- [There is a system-wide LWLock, the BufMappingLock, that notionally protects the mapping from buffer tags (page identifiers) to buffers.](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/README#L123)
- `BufMappingLock` — 버퍼 태그(page id) → 버퍼 매핑을 보호하는 해시 락. 파티션 분할되어 있어(PG 8.2+ `NUM_BUFFER_PARTITIONS`) 서로 다른 파티션 접근은 동시에 가능하다.
- `buffer_strategy_lock` — clock-sweep의 `nextVictimBuffer` 이동을 보호하는 별도의 전역 스핀락. victim 탐색(LRU 정책, [lru.md](./lru.md) 참고) 전용이라 매핑 조회와 락 경합이 없다.
- 버퍼 헤더별 스핀락 — 헤더의 usage counter/refcount 갱신 처럼, 헤더의 필드를 변경할 경우 사용하는 짧은 락.

즉 PostgreSQL은 "① 파티션 분할된 매핑 락 ② victim 탐색 전용 전역 락 ③ 페이지 내용 보호용 content lock"으로 InnoDB(뮤텍스 6종 분리, [innodb-lock.md](../MySQL/innodb-lock.md) 참고)보다 더 잘게 나뉘어 있다.

### 버전 — PostgreSQL 19부터

`content_lock`을 LWLock에서 떼어내 전용 구현으로 바꾼 커밋(Andres Freund, "bufmgr: Implement buffer content locks independently of lwlocks")은 **PostgreSQL 19**(2026년 기준 개발 중인 버전)에 들어갔다. 이 내용을 설명하는 공식 SGML 매뉴얼 페이지는 없다 — 내부 구현 디테일이라 사용자 매뉴얼 범위 밖이고, 가장 신뢰할 수 있는 출처는 다음 세 가지뿐이다.

1. 소스 코드 주석 자체 (`buf_internals.h`, `README`)
2. 커밋 메시지: [E1vgT1S-000fPa-35@gemulon.postgresql.org](https://www.postgresql.org/message-id/E1vgT1S-000fPa-35%40gemulon.postgresql.org)
3. Andres Freund 본인의 발표자료 — [AIO in Postgres 18 and beyond](https://anarazel.de/talks/2025-10-23-pgconf-eu-aio-in-PG-18-and-beyond/aio-in-PG-18-and-beyond.pdf), [IO in PostgreSQL: Past, Present, Future](https://anarazel.de/talks/2022-05-02-vaccination-db-aio/2022-05-02-vaccination-db-aio.pdf)

### 예전(PG16) — 원자적 워드가 2개, 락 모드는 2개

[buf_internals.h (PG16)](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/include/storage/buf_internals.h#L244)
```c
// buf_internals.h (PG16, 커밋 9a0cd8e73f52307162f0c81e2d6e52c79f5592c3)
typedef struct BufferDesc
{
	BufferTag	tag;
	int			buf_id;
	pg_atomic_uint32 state;        // ① 헤더 자신의 상태 (flags+refcount+usagecount)
	int			wait_backend_pgprocno;
	int			freeNext;
	LWLock		content_lock;       // ② 콘텐츠 락 — 완전히 별개의 원자적 객체
} BufferDesc;
```

- `LWLock content_lock`은 **자기만의 별도 `pg_atomic_uint32 state`를 갖는 독립된 객체**다. 즉 버퍼 하나당 원자적 워드가 (헤더용 + LWLock용) **2개** 따로 있었다.
- LWLock 자체는 이미 CAS 기반이었다 — 지금 쓰이는 방식과 거의 동일한 패턴.

[lwlock.c (PG16)](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/backend/storage/lmgr/lwlock.c#L810-L841)
```c
// lwlock.c:810-841 (PG16)
static bool
LWLockAttemptLock(LWLock *lock, LWLockMode mode)
{
	uint32 old_state = pg_atomic_read_u32(&lock->state);
	while (true) {
		bool lock_free;
		uint32 desired_state = old_state;
		if (mode == LW_EXCLUSIVE) {
			lock_free = (old_state & LW_LOCK_MASK) == 0;
			if (lock_free) desired_state += LW_VAL_EXCLUSIVE;
		} else {
			lock_free = (old_state & LW_VAL_EXCLUSIVE) == 0;
			if (lock_free) desired_state += LW_VAL_SHARED;
		}
		// CAS 시도, 실패하면 old_state 갱신 후 재루프
	}
}
```

`LW_VAL_SHARED = 1`(카운터), `LW_VAL_EXCLUSIVE = 1 << 24`(플래그 1비트) — **모드는 SHARE/EXCLUSIVE 2개뿐**, `LW_VAL_SHARE_EXCLUSIVE`류 상수 자체가 없다.

**실제 코드 레벨 요구사항도 2개 모드로 충분했다** — README의 "3종류" 서술은 오래전부터 있었지만, 정작 코드는 이렇게 느슨했다.

[bufmgr.c — MarkBufferDirtyHint (PG16)](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/backend/storage/buffer/bufmgr.c#L4626), [bufmgr.c — FlushBuffer (PG16)](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/backend/storage/buffer/bufmgr.c#L3400)
```c
// bufmgr.c — MarkBufferDirtyHint (hint bit 갱신)
Assert(GetPrivateRefCount(buffer) > 0);
/* here, either share or exclusive lock is OK */
Assert(LWLockHeldByMe(BufferDescriptorGetContentLock(bufHdr)));

// bufmgr.c — FlushBuffer 직전 주석 (버퍼 write-out)
/*
 * The caller must hold a pin on the buffer and have share-locked the
 * buffer contents.  (Note: a share-lock does not prevent updates of
 * hint bits in the buffer, so the page could change while the write
 * is in progress, but we assume that that will not invalidate the data
 * written.)
 */
```

즉 hint bit 갱신은 "share든 exclusive든 상관없음", 버퍼 write-out은 "그냥 SHARE면 됨" — 이 둘이 동시에 일어나는 걸 막는 진짜 3번째 모드는 없었고, 그 결과 "write 도중 hint bit가 바뀔 수 있다"는 걸 코드가 스스로 인정하고 있었다. 체크섬이 켜져 있으면 이 문제를 피하려고 write할 페이지를 통째로 복사해야 했다.

### 최신(PG19) — 원자적 워드 1개, 락 모드 3개

[buf_internals.h (master)](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/include/storage/buf_internals.h#L305), [bufmgr.h (master)](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/include/storage/bufmgr.h#L203-L222)
```c
// buf_internals.h (master, 커밋 c12c101b0846b1e6488f2dc986a852fbc6bf2e3b)
typedef struct BufferDesc
{
	BufferTag	tag;
	int			buf_id;
	pg_atomic_uint64 state;       // refcount+usagecount+flags+lock을 전부 통합
	int			wait_backend_pgprocno;
	PgAioWaitRef io_wref;          // set iff AIO is in progress
	proclist_head lock_waiters;
} BufferDesc;

typedef enum BufferLockMode
{
	BUFFER_LOCK_UNLOCK,
	BUFFER_LOCK_SHARE,
	BUFFER_LOCK_SHARE_EXCLUSIVE,   // 진짜 3번째 모드
	BUFFER_LOCK_EXCLUSIVE,
} BufferLockMode;
```

> "We used to use an LWLock to implement the content lock, but having a dedicated implementation of content locks allows us to implement some otherwise hard things (e.g. race-freely checking if AIO is in progress before locking a buffer exclusively) and enables otherwise impossible optimizations (e.g. unlocking and unpinning a buffer in one atomic operation)."

#### `state`(64bit) 비트 레이아웃

[buf_internals.h (master)](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/include/storage/buf_internals.h#L49-L86)
```c
#define BUF_REFCOUNT_BITS 18
#define BUF_USAGECOUNT_BITS 4
#define BUF_FLAG_BITS 12
#define BUF_LOCK_BITS (18+2)   // MAX_BACKENDS_BITS(18) + SHARE_EXCLUSIVE(1) + EXCLUSIVE(1)
#define BM_LOCK_SHIFT (BUF_FLAG_SHIFT + BUF_FLAG_BITS)   // = 34
```

계산하면 실제 비트 위치는 이렇다(64비트 중 54비트만 사용, 상위 10비트는 예약):

```
bit63 ~ bit54   미사용/예약 (10비트)
bit53           EXCLUSIVE           ← 가장 상위(왼쪽)
bit52           SHARE_EXCLUSIVE
bit51 ~ bit34   SHARE count (18비트 = MAX_BACKENDS_BITS, 동시 SHARE 보유자 수를 센다)
bit33 ~ bit22   FLAGS (12비트: BM_LOCKED, BM_DIRTY, BM_IO_IN_PROGRESS, BM_LOCK_HAS_WAITERS 등)
bit21 ~ bit18   USAGECOUNT (4비트)
bit17 ~ bit0    REFCOUNT (18비트, = pin count)
```

`state`는 **"누가" 락을 쥐고 있는지는 담지 않는다.** SHARE_EXCLUSIVE/EXCLUSIVE 비트는 있다/없다만 표시하는 플래그고, SHARE는 몇 명이 쥐고 있는지 카운트만 한다. "내가 쥐고 있는지"는 각 백엔드가 자기 프로세스 로컬 메모리(`PrivateRefCountEntry.data.lockmode`)에 따로 기억한다 — 재진입(같은 버퍼를 같은 백엔드가 두 번 락 거는 것)은 애초에 assert로 막혀 있어서 "내 것인지 남의 것인지" 판별할 필요 자체가 없다.

#### CAS 기반 획득 시도 — `BufferLockAttempt`

[bufmgr.c (master) — BufferLockAttempt](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/bufmgr.c#L6119)
```c
static inline bool
BufferLockAttempt(BufferDesc *buf_hdr, BufferLockMode mode)
{
	uint64 old_state = pg_atomic_read_u64(&buf_hdr->state);   // 스냅샷

	while (true) {
		uint64 desired_state = old_state;
		bool lock_free;

		if (mode == BUFFER_LOCK_EXCLUSIVE) {
			lock_free = (old_state & BM_LOCK_MASK) == 0;
			if (lock_free) desired_state += BM_LOCK_VAL_EXCLUSIVE;
		} else if (mode == BUFFER_LOCK_SHARE_EXCLUSIVE) {
			lock_free = (old_state & (BM_LOCK_VAL_EXCLUSIVE | BM_LOCK_VAL_SHARE_EXCLUSIVE)) == 0;
			if (lock_free) desired_state += BM_LOCK_VAL_SHARE_EXCLUSIVE;
		} else {
			lock_free = (old_state & BM_LOCK_VAL_EXCLUSIVE) == 0;
			if (lock_free) desired_state += BM_LOCK_VAL_SHARED;
		}

		if (likely(pg_atomic_compare_exchange_u64(&buf_hdr->state, &old_state, desired_state))) {
			if (lock_free) return false;   // 락 획득 성공
			else return true;              // 이미 남이 쥠 (대기 필요)
		}
		// CAS 실패 시 old_state가 최신값으로 자동 갱신되어 재루프
	}
}
```

- `old_state`를 CAS의 `expected`로 넘기는 것 자체가 "그 사이에 값이 바뀌었는지" 검사하는 역할을 겸한다. 실패하면 `pg_atomic_compare_exchange_u64`가 `old_state`를 최신값으로 자동 갱신해주므로 별도 재조회 없이 루프를 돌 수 있다.
- **락을 못 잡는 걸 이미 아는 경우(`lock_free == false`)에도 굳이 CAS를 시도한다.** 이유는 CAS 자체가 메모리 배리어 효과를 겸하기 때문 — `if (!lock_free) return true;`로 바로 빠지면 이 검사 전후의 메모리 순서 보장이 사라진다. 벤치마크상으로도 이 스킵 최적화가 이득이 없었다고 주석에 명시.

이걸로 보면 진짜 바뀐 건 "lock-free(CAS) 방식을 새로 도입한 것"이 아니라, **원래도 CAS 기반이었던 두 개의 독립된 원자적 객체(헤더 state + LWLock)를 하나로 합치고, 락 모드를 3개로 늘린 것**이다. 통합 덕분에 "pin 해제 + 락 해제"처럼 예전엔 두 번의 원자적 연산이 필요했던 걸 한 번의 CAS로 끝낼 수 있게 됐다.

#### 그래도 안 되면 — `PGSemaphoreLock`으로 진짜 블로킹

[bufmgr.c (master) — BufferLockAcquire](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/bufmgr.c#L5921)
```c
static inline void
BufferLockAcquire(Buffer buffer, BufferDesc *buf_hdr, BufferLockMode mode)
{
	PrivateRefCountEntry *entry = GetPrivateRefCountEntry(buffer, true);
	int extraWaits = 0;

	Assert(entry->data.lockmode == BUFFER_LOCK_UNLOCK);   // 재진입 금지
	HOLD_INTERRUPTS();   // 락을 쥔 동안 내내 취소/종료 시그널 홀드

	for (;;) {
		bool mustwait = BufferLockAttempt(buf_hdr, mode);   // 1차 시도
		if (likely(!mustwait)) break;

		BufferLockQueueSelf(buf_hdr, mode);                 // 대기 큐 등록
		mustwait = BufferLockAttempt(buf_hdr, mode);        // 2차 시도(놓친 wakeup 방지)
		if (!mustwait) { BufferLockDequeueSelf(buf_hdr); break; }

		// wait_event 기록 후 진짜로 잠듦
		for (;;) {
			PGSemaphoreLock(MyProc->sem);
			if (MyProc->lwWaiting == LW_WS_NOT_WAITING) break;
			extraWaits++;   // spurious wakeup 흡수
		}
		pg_atomic_fetch_and_u64(&buf_hdr->state, ~BM_LOCK_WAKE_IN_PROGRESS);
	}
	entry->data.lockmode = mode;   // 로컬에 "내가 이 모드로 쥐고 있음" 기록
	while (unlikely(extraWaits-- > 0)) PGSemaphoreUnlock(MyProc->sem);
}
```

- **1차 시도 → 실패하면 큐 등록 → 큐 등록 후 2차 시도(놓친 wakeup 방지) → 그래도 실패하면 진짜로 잠듦**, LWLock의 획득 알고리즘과 거의 동일한 구조(주석에 "Similar to LWLockAttemptLock()"이라고 명시).
- `PGSemaphoreLock(MyProc->sem)`은 **백엔드 프로세스 하나당 배정된 실제 OS 세마포어**를 통한 블로킹이다. CAS/스핀락과는 완전히 다른 계층: CAS는 유저스페이스에서 나노초 단위로 끝나는 원자적 연산이고, 세마포어 대기는 커널이 개입해 프로세스를 실제로 재우는(컨텍스트 스위치 발생) 것 — 락을 쥔 다른 백엔드가 풀리면서 `PGSemaphoreUnlock`으로 깨워준다.
- `HOLD_INTERRUPTS()`는 락 메커니즘 자체가 아니라, **락을 쥐고 있는 동안 취소(cancel)/종료(die) 시그널이 중간에 끼어들어 공유 메모리를 반쯤 갱신된 상태로 남기지 않도록** 하는 안전장치다. 락 해제까지 유지된다.

#### 네이티브 CAS가 없는 플랫폼의 폴백 — 스핀락

[atomics.c](https://github.com/postgres/postgres/blob/60826a352d497b15a30de29b7796d0c2d097a0e3/src/backend/port/atomics.c#L34)
```c
// atomics.c — pg_atomic_compare_exchange_u64_impl
SpinLockAcquire((slock_t *) &ptr->sema);
ret = ptr->value == *expected;
*expected = ptr->value;
if (ret) ptr->value = newval;
SpinLockRelease((slock_t *) &ptr->sema);
```

필드 이름은 `sema`지만 `(slock_t *)`로 캐스팅되어 실제로는 **스핀락**으로 쓰인다(역사적으로 세마포어로 원자적 연산을 흉내내던 시절의 흔적으로 추정). 이건 `MyProc->sem`(진짜 프로세스 세마포어, 락 경합 대기용)과는 완전히 다른 객체다 — 이 스핀락은 "64비트 워드 하나를 비교/교체"하는 나노초 단위 작업만 보호한다.

### 왜 바꿨나 — 실제 커밋 메시지 근거

1. **Hint bit + 체크섬 문제**: "Hint bits are currently set while holding only a share lock. This leads to having to copy pages while they are being written out if checksums are enabled, which is not cheap." AIO로 한 번에 훨씬 많은 버퍼가 동시에 쓰여지면 이 복사 비용이 급증한다. → SHARE_EXCLUSIVE 도입으로 hint bit 갱신과 write-out을 상호배타적으로 만들어 해결.
2. **AIO 레이스**: "For AIO writes we need to be able to race-freely check whether a buffer is undergoing IO and whether an exclusive lock on the page can be acquired. That is rather hard to do efficiently when the buffer state and the lock state are separate atomic variables." — 동기 I/O 시절엔 한 백엔드가 `pwrite()`로 블로킹되는 동안은 사실상 그 버퍼를 독점하는 셈이라 문제가 안 됐지만, AIO로 여러 버퍼가 동시에 in-flight 상태가 되면서 "IO 중인지"와 "락을 잡을 수 있는지"를 별개의 원자적 변수로 나눠서 체크하는 게 실질적인 TOCTOU 레이스가 됐다.
3. **성능**: "Buffer locks are by far the most frequently taken locks... by merging content locks into buffer locks we will be able to release a buffer lock and pin in one atomic operation."
4. **미래 확장성**: "long-lived 'super pinned & locked' pages" 같은 최적화는 범용 LWLock 구조로는 구현 불가능.

동기식 I/O가 실제로 OS 블로킹 syscall이었다는 것도 소스로 확인된다.

[fd.c (PG16)](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/backend/storage/file/fd.c#L2111), [port.h (PG16)](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/include/port.h#L227-L228)
```c
// fd.c — FileWrite/FileRead
returnCode = pg_pwrite(VfdCache[file].fd, buffer, amount, offset);
returnCode = pg_pread(vfdP->fd, buffer, amount, offset);

// port.h
#define pg_pread  pread
#define pg_pwrite pwrite
```

`FlushBuffer()`가 이 `pwrite()`를 호출하는 동안 해당 버퍼의 content lock을 쥔 채로 진행되므로, 락 보유 시간이 "메모리 CAS 몇 나노초"가 아니라 "커널 I/O가 끝날 때까지"로 늘어나는 구간이 실제로 존재했다. AIO는 이 블로킹 자체를 없애 백엔드가 I/O를 제출만 하고 바로 다른 일을 하게 만드는데, 그러려면 "지금 이 버퍼를 누가 비동기로 쓰고 있는 중인가"를 정확히 추적할 방법이 필요했고, 그게 이번 리팩터링의 핵심 동기다.

### 참고 자료

- [pgsql: bufmgr: Implement buffer content locks independently of lwlocks](https://www.postgresql.org/message-id/E1vgT1S-000fPa-35%40gemulon.postgresql.org)
- [PG16 buf_internals.h](https://github.com/postgres/postgres/blob/9a0cd8e73f52307162f0c81e2d6e52c79f5592c3/src/include/storage/buf_internals.h#L244)
- [master(PG19) buf_internals.h](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/include/storage/buf_internals.h#L305)
- [master bufmgr.c의 실제 Lock 구현](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/buffer/bufmgr.c#L5921)
