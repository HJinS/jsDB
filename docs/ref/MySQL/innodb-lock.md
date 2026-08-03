## InnoDB rw_lock_t — 버퍼 콘텐츠 락 딥다이브

> MySQL의 innodb 스토리지 엔진에서 buffer 내부 데이터의 lock을 어떻게 관리하고 있는지에 관한 내용이다. MySQL의 `row/table/gap lock` 과는 다른, 좀 더 `row-level`의 lock이다.

### 메타데이터 락 — 뮤텍스 6종 분리

**[buf0buf.cc 개요 주석](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L134)** 에 버퍼풀을 보호하는 뮤텍스 목록이 정리되어 있다.

| 뮤텍스 | 보호 대상 |
|---|---|
| `chunks_mutex` | chunks, n_chunks(리사이징 중), madvise 상태 |
| `LRU_list_mutex` | LRU_list |
| `free_list_mutex` | free_list, withdraw list |
| `flush_state_mutex` | flush 상태 관련 자료구조 |
| `zip_free` | zip_free 배열 |
| `zip_hash` | zip_hash 해시, in_zip_hash 플래그 |

버퍼 프레임(페이지 내용) 자체는 이 뮤텍스들과 별개로, 제어 블록마다 있는 **읽기-쓰기 락**(`block->lock` = `rw_lock_t`, 아래 절 참고)으로 보호한다. 즉 "메타데이터(LRU/free-list/flush 등)를 보호하는 여러 개의 짧은 뮤텍스" + "페이지 내용을 보호하는 프레임별 rw-lock" 두 계층으로 나뉘어 있다.

> [The bufferfix operation does not lock the contents of the frame, however. For this purpose, the control block contains a read-write lock.](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L171-L172)

### 컨트롤 블록 구조

`buf_fix_count`(pin count)와 `io_fix`(IO 진행 플래그)는 `buf_page_t`에,
락 객체(`BPageLock` = `rw_lock_t`)는 `buf_block_t`에 **별도 필드**로 있다.
[postgres-content-lock.md](../PostgreSQL/content-lock.md)에서 본 PG16(리팩터 이전) 구조와 개념적으로 같다
PG19가 이걸 하나의 원자적 워드로 합친 것과 달리, InnoDB는 지금도 분리된 채로 유지하고 있다.

- [buf_block_t](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/buf0buf.h#L1756)
- [buf_page_t](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/buf0buf.h#L1156)
```cpp
// buf0buf.h - buf_block_t
struct buf_block_t {
  buf_page_t page;          // buf_fix_count(pin), io_fix(IO 상태) 등
  BPageLock  lock;           // = rw_lock_t, 컨트롤 블록과 별개의 필드
  byte      *frame;
  ...
};

// buf_page_t
class buf_page_t {
 public:
  /** Copy constructor.
  @param[in] other       Instance to copy from. */
    buf_page_t(const buf_page_t &other)
      : id(other.id),
        size(other.size),
        buf_fix_count(other.buf_fix_count),
        io_fix(other.io_fix),
        state(other.state),
        flush_type(other.flush_type),
        buf_pool_index(other.buf_pool_index),
#ifndef UNIV_HOTBACKUP
        hash(other.hash),
#endif /* !UNIV_HOTBACKUP */
        list(other.list),
        newest_modification(other.newest_modification),
        oldest_modification(other.oldest_modification),
        LRU(other.LRU),
        zip(other.zip)
#ifndef UNIV_HOTBACKUP
        ,
        m_flush_observer(other.m_flush_observer),
        m_space(other.m_space),
        freed_page_clock(other.freed_page_clock),
        m_version(other.m_version),
        access_time(other.access_time),
        m_dblwr_id(other.m_dblwr_id),
        old(other.old)
#ifdef UNIV_DEBUG
        ,
        file_page_was_freed(other.file_page_was_freed),
        in_flush_list(other.in_flush_list),
        in_free_list(other.in_free_list),
        in_LRU_list(other.in_LRU_list),
        in_page_hash(other.in_page_hash),
        in_zip_hash(other.in_zip_hash)
#endif /* UNIV_DEBUG */
#endif /* !UNIV_HOTBACKUP */
  {
#ifndef UNIV_HOTBACKUP
    m_space->inc_ref();
#endif /* !UNIV_HOTBACKUP */
  }
  ...
};
```

### S/SX/X 3단계 — 비트마스크가 아니라 정수 카운터 트릭

[sync0rw.h - rw_lock_t](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.h#L362)
```cpp
// sync0rw.h

/* Latch types; these are used also in btr0btr.h and mtr0mtr.h: keep the
numerical values smaller than 30 (smaller than BTR_MODIFY_TREE and
MTR_MEMO_MODIFY) and the order of the numerical values like below! and they
should be 2pow value to be used also as ORed combination of flag. */
enum rw_lock_type_t {
  RW_S_LATCH = 1,
  RW_X_LATCH = 2,
  RW_SX_LATCH = 4,
  RW_NO_LATCH = 8
};

struct rw_lock_t {
  std::atomic<int32_t> lock_word;   // 락 상태 전체를 담는 카운터
  std::atomic<bool> waiters;        // 대기자 존재 여부
  std::atomic<bool> recursive;      // 재진입 가능 여부
  std::atomic<uint64_t> sx_recursive;
  std::atomic<std::thread::id> writer_thread;
  Atomic_xor_of_thread_id reader_thread;
  os_event_t event;
  os_event_t wait_ex_event;
  ...
};
```

InnoDB는 PostgreSQL처럼 비트필드를 나누는 대신, **정수 하나를 서로 다른 크기로 깎는** 방식으로 S/SX/X 3단계를 구현한다.

[sync0rw.h](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.h#L107-L108)
```cpp
/* We decrement lock_word by X_LOCK_DECR for each x_lock. It is also the
start value for the lock_word, meaning that it limits the maximum number
of concurrent read locks before the rw_lock breaks. */
/* We decrement lock_word by X_LOCK_HALF_DECR for sx_lock. */
constexpr int32_t X_LOCK_DECR = 0x20000000;
constexpr int32_t X_LOCK_HALF_DECR = 0x10000000;
```

[sync0rw.ic - CAS를 통한 lock 획득 함수](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.ic#L211-L226)
```c
// sync0rw.ic — 실제 CAS 루프
/** Two different implementations for decrementing the lock_word of a rw_lock:
 one for systems supporting atomic operations, one for others. This does
 does not support recursive x-locks: they should be handled by the caller and
 need not be atomic since they are performed by the current lock holder.
 Returns true if the decrement was made, false if not.
 @return true if decr occurs */
ALWAYS_INLINE
bool rw_lock_lock_word_decr(rw_lock_t *lock, /*!< in/out: rw-lock */
                            ulint amount,    /*!< in: amount to decrement */
                            lint threshold)  /*!< in: threshold of judgement */
{
  int32_t local_lock_word;

  os_rmb;
  local_lock_word = lock->lock_word;
  while (local_lock_word > threshold) {
    if (lock->lock_word.compare_exchange_strong(local_lock_word,
                                                local_lock_word - amount)) {
      return (true);
    }
  }
  return (false);
}
```

[실제 lock을 획득하는 함수](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/sync/sync0rw.cc#L445-L560)
```cpp
// sync0rw.cc
/** Low-level function for acquiring an exclusive lock.
 @return false if did not succeed, true if success. */
static inline bool rw_lock_x_lock_low(
    rw_lock_t *lock,       /*!< in: pointer to rw-lock */
    ulint pass,            /*!< in: pass value; != 0, if the lock will
                           be passed to another thread to unlock */
    const char *file_name, /*!< in: file name where lock requested */
    ulint line)            /*!< in: line where requested */
{
  if (rw_lock_lock_word_decr(lock, X_LOCK_DECR, X_LOCK_HALF_DECR)) {
    /* lock->recursive == true implies that the lock->writer_thread is the
    current writer. As we are going to write our own thread id in that field it
    must be the case that the current writer_thread value is not the current
    writer anymore, thus recursive flag must be false.  */
    ut_a(!lock->recursive.load(std::memory_order_relaxed));

    /* Decrement occurred: we are writer or next-writer. */
    rw_lock_set_writer_id_and_recursion_flag(lock, !pass);

    rw_lock_x_lock_wait(lock, pass, 0, file_name, line);

  } else {
    if (!pass && lock->recursive.load(std::memory_order_acquire) &&
        lock->writer_thread.load(std::memory_order_relaxed) ==
            std::this_thread::get_id()) {
      /* Decrement failed: An X or SX lock is held by either
      this thread or another. Try to relock. */
      /* Other s-locks can be allowed. If it is request x
      recursively while holding sx lock, this x lock should
      be along with the latching-order. */

      /* The existing X or SX lock is from this thread */
      if (rw_lock_lock_word_decr(lock, X_LOCK_DECR, 0)) {
        /* There is at least one SX-lock from this
        thread, but no X-lock. */

        /* Wait for any the other S-locks to be
        released. */
        rw_lock_x_lock_wait(lock, pass, -X_LOCK_HALF_DECR, file_name, line);

      } else {
        /* At least one X lock by this thread already
        exists. Add another. */
        if (lock->lock_word == 0 || lock->lock_word == -X_LOCK_HALF_DECR) {
          lock->lock_word -= X_LOCK_DECR;
        } else {
          ut_ad(lock->lock_word <= -X_LOCK_DECR);
          --lock->lock_word;
        }
      }
    } else {
      /* Another thread locked before us */
      return false;
    }
  }

  ut_d(rw_lock_add_debug_info(lock, pass, RW_LOCK_X, {file_name, line}));

  lock->last_x_file_name = file_name;
  ut_ad(line <= std::numeric_limits<decltype(lock->last_x_line)>::max());
  lock->last_x_line = line;

  return true;
}

bool rw_lock_sx_lock_low(rw_lock_t *lock, ulint pass, ut::Location location) {
  if (rw_lock_lock_word_decr(lock, X_LOCK_HALF_DECR, X_LOCK_HALF_DECR)) {
    /* lock->recursive == true implies that the lock->writer_thread is the
    current writer. As we are going to write our own thread id in that field it
    must be the case that the current writer_thread value is not the current
    writer anymore, thus recursive flag must be false.  */
    ut_a(!lock->recursive.load(std::memory_order_relaxed));

    /* Decrement occurred: we are the SX lock owner. */
    rw_lock_set_writer_id_and_recursion_flag(lock, !pass);

    lock->sx_recursive.store(1, std::memory_order_relaxed);

  } else {
    /* Decrement failed: It already has an X or SX lock by this
    thread or another thread. If it is this thread, relock,
    else fail. */
    if (!pass && lock->recursive.load(std::memory_order_acquire) &&
        lock->writer_thread.load(std::memory_order_relaxed) ==
            std::this_thread::get_id()) {
      /* This thread owns an X or SX lock */
      if (lock->increment_sx_recursive() == 0) {
        /* This thread is making first SX-lock request
        and it must be holding at least one X-lock here
        because:

        * There can't be a WAIT_EX thread because we are
          the thread which has it's thread_id written in
          the writer_thread field and we are not waiting.

        * Any other X-lock thread cannot exist because
          it must update recursive flag only after
          updating the thread_id. Had there been
          a concurrent X-locking thread which succeeded
          in decrementing the lock_word it must have
          written it's thread_id before setting the
          recursive flag. As we cleared the if()
          condition above therefore we must be the only
          thread working on this lock and it is safe to
          read and write to the lock_word. */

        ut_ad((lock->lock_word == 0) ||
              ((lock->lock_word <= -X_LOCK_DECR) &&
               (lock->lock_word > -(X_LOCK_DECR + X_LOCK_HALF_DECR))));
        lock->lock_word -= X_LOCK_HALF_DECR;
      }
    } else {
      /* Another thread locked before us */
      return false;
    }
  }
```

| 락 | 호출 | 의미 |
|---|---|---|
| S-lock | `rw_lock_lock_word_decr(lock, 1, 0)` | 1만 깎고, 남은 값이 `>0`이면 성공 |
| X-lock | `rw_lock_lock_word_decr(lock, X_LOCK_DECR, X_LOCK_HALF_DECR)` | 전체(`X_LOCK_DECR`)를 깎고, 남은 값이 `X_LOCK_HALF_DECR`보다 크면 성공 |
| SX-lock | `rw_lock_lock_word_decr(lock, X_LOCK_HALF_DECR, X_LOCK_HALF_DECR)` | 절반만 깎고, 남은 값이 그 threshold보다 커야 성공 |

- `lock_word`는 `X_LOCK_DECR`(완전히 빈 상태)에서 시작한다.
- S-lock끼리는 각자 1씩만 깎으니 서로 안 막힌다.
- **X-lock은 "완전히 비어야만 성공"이 아니라 2단계로 나뉜다.** threshold가 `X_LOCK_DECR-1`이 아니라 `X_LOCK_HALF_DECR`라서, S-lock 몇 개가 이미 걸려있어도(각자 1씩만 깎으니 `lock_word`가 여전히 `X_LOCK_HALF_DECR`보다 훨씬 위) CAS는 그냥 성공한다.
  - 주의할 점은 threshold비교를 먼저 하고 그 후에 CAS를 진행한다.
    1. **1단계(예약)**: `rw_lock_lock_word_decr`가 성공하는 순간 `lock_word`가 0 이하로 떨어져서 **새로운 S/SX 시도는 즉시 다 막힌다**(writer 굶주림 방지). 이 시점에 `rw_lock_set_writer_id_and_recursion_flag(lock, !pass)`로 `writer_thread`/`recursive`를 바로 갱신해둔다.
    2. **2단계(대기)**: `rw_lock_x_lock_wait(lock, pass, 0, ...)`가 `lock_word`가 다시 정확히 0으로 돌아올 때까지(=CAS 시점에 이미 걸려있던 기존 S-lock들이 전부 풀릴 때까지) 스핀 후 블로킹한다. 이때까지는 "예약"만 된 상태고, 진짜 배타적 접근은 2단계가 끝나야 확보된다.
- SX-lock은 "X 예산의 절반"만 미리 떼어가는 트릭이다 — 다른 SX/X가 오면(둘 다 큰 덩어리 요구) 남은 값이 threshold 밑으로 떨어져 실패하지만(SX는 SX/X와 배타적), S-lock(1씩만 깎음)은 여전히 통과할 여유가 남아있다(SX는 S와 안 막힘). PostgreSQL의 `BUFFER_LOCK_SHARE_EXCLUSIVE`와 정확히 같은 시맨틱을 완전히 다른 산술적 방식으로 구현한 것이다.

**CAS 재시도 루프 패턴 자체는 PG의 `BufferLockAttempt`와 동일**하다 — 스냅샷 읽기 → 로컬에서 새 값 계산 → CAS, 실패 시 자동 갱신된 값으로 재루프.

#### 왜 `X_LOCK_DECR`가 `INT32_MAX`가 아니라 `2^29`인가

`lock_word`는 양수(S-lock 누적)뿐 아니라 음수 쪽으로도 여유가 필요하다. 같은 스레드가 X를 쥔 채로 SX까지 재귀적으로 요청하면:

[sync0rw.ic - rw_lock_x_unlock_func](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.ic#L379-L418)
```cpp
ut_ad(lock->lock_word == -X_LOCK_DECR ||
      lock->lock_word == -(X_LOCK_DECR + X_LOCK_HALF_DECR));  // 최저점
```

최저점은 `-(X_LOCK_DECR + X_LOCK_HALF_DECR)` = **`-1.5 × X_LOCK_DECR`**다.
`int32_t`는 양수 쪽 한계(`2^31-1`)와 음수 쪽 한계(`-2^31`)가 서로 독립적인 제약이라, 여기서 봐야 할 건 이 음수 쪽으로 얼마나 내려가는지(`1.5 × X_LOCK_DECR`)뿐이다.
`X_LOCK_DECR`를 `INT32_MAX`까지 밀어붙였다면 `1.5 × INT32_MAX`가 `int32_t` 최솟값(`-2^31`)을 훌쩍 넘어 signed overflow가 났을 것이다.
`2^29`는 위(S-lock 동시 보유자 수)로도 아래(`1.5 × 2^29` ≈ 8억, `2^31`인 약 21억까지 여유)로도 넉넉한 여유를 남기는 라운드 넘버다.

### 스레드 신원 추적 — PostgreSQL과 가장 크게 갈리는 지점

[sync0rw.h - rw-lock_t](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.h#L405-L411)
```cpp
std::atomic<std::thread::id> writer_thread;      // X-lock을 쥔 스레드
Atomic_xor_of_thread_id reader_thread;             // reader가 1명일 때 그 스레드까지 XOR 트릭으로 추적
```

> InnoDB는 락 객체 안에 **소유자 스레드 신원을 직접 저장**한다.
> PostgreSQL의 `BufferDesc.state`는 카운트/플래그만 담고 "누가" 쥐고 있는지는 전혀 담지 않는다(신원은 각 백엔드의 로컬 `PrivateRefCountEntry`에만 있음).

**왜 갈렸나 — reentrant(재진입) 허용 여부가 근본 원인이다.**

- InnoDB는 같은 스레드가 이미 쥔 락을 다시 요청해도 통과시켜준다(`recursive` 플래그). 이걸 판정하려면 "요청자가 현재 소유자와 동일한가"를 알아야 하고, 그러려면 소유자 신원 저장이 필수다.
- PostgreSQL은 재진입 자체를 API 레벨에서 금지한다(`Assert(entry->data.lockmode == BUFFER_LOCK_UNLOCK)`). "같은 백엔드가 같은 버퍼를 두 번 락 거는 일은 일어나면 안 된다"고 못박아버렸으니, "요청자가 기존 소유자와 같은가"를 물을 필요 자체가 없어진다.

#### "소유자만 unlock 가능"이 실제로 강제되는 곳

`rw_lock_remove_debug_info()`(디버그 빌드 전용, `.ic`/`.cc`의 `ut_d(...)`로 감싸진 호출들이 여기로 들어옴)에서 직접 체크한다.

[sync0rw.cc](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/sync/sync0rw.cc#L827)
```cpp
// sync0rw.cc
void rw_lock_remove_debug_info(rw_lock_t *lock, ulint pass, ulint lock_type) {
  ...
  for (auto info : lock->debug_list) {
    if (pass == info->pass &&
        (pass != 0 || info->thread_id == std::this_thread::get_id()) &&   // pass==0이면 스레드 ID 일치 필수
        info->lock_type == lock_type) {
      /* Found! */
      UT_LIST_REMOVE(lock->debug_list, info);
      ...
      return;
    }
  }
  ...
  ut_error;   // 매칭되는 기록을 못 찾으면 크래시
}
```

`pass == 0`(일반적인 unlock)인데 현재 스레드 ID가 `debug_list`의 어떤 기록과도 안 맞으면 루프를 다 돌고 `ut_error`로 죽는다
"엉뚱한 스레드가 남의 락을 풀려는" 상황을 디버그 빌드에서 즉시 잡아내는 방어장치다.
`pass != 0`(AIO용)일 때만 이 스레드 ID 매칭을 건너뛴다

### AIO와 락 소유권 이전 — `pass` 파라미터

[buf0buf.cc - buf_page_init_for_read](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L4972-L5000)
```cpp
buf_page_set_io_fix(bpage, BUF_IO_READ);

/* We set a pass-type x-lock on the frame because then
the same thread which called for the read operation
(and is running now at this point of code) can wait
for the read to complete by waiting for the x-lock on
the frame; if the x-lock were recursive, the same
thread would illegally get the x-lock before the page
read is completed. The x-lock is cleared by the
io-handler thread. */
rw_lock_x_lock_gen(&block->lock, BUF_IO_READ, UT_LOCATION_HERE);
```

InnoDB의 `rw_lock_t`는 X-lock을 건 스레드만 풀 수 있다는 규칙(`writer_thread` 체크)이 있는데, **AIO 완료는 락을 건 스레드가 아니라 별도의 AIO 완료 처리 스레드에서 일어날 수 있다.**
`pass != 0`으로 락을 걸면 이 스레드 소유권 체크를 의도적으로 우회해서 "이 락은 나중에 다른 스레드가 대신 풀 수도 있다"고 표시해둔다
X-lock이 디스크 읽기 전체 기간 동안 걸려 있다가, AIO 완료 스레드가 대신 풀어주는 구조다.

실제 호출부 주석에 `pass`를 쓰는 진짜 이유가 더 정확히 나와 있다.

> 즉 `pass`가 필요한 이유는 단순히 "다른 스레드가 풀 수 있게" 뿐만 아니라, **같은 스레드가 나중에 "이 읽기가 끝나길 기다리려고" 다시 X-lock을 요청했을 때 `recursive` 플래그 때문에 즉시 통과해버리는 걸 막기 위해서**다.
> `pass`가 없었다면 `recursive==true && writer_thread==self`로 판정돼서, 읽기가 실제로 끝나지도 않았는데 "나는 이미 이 락을 쥐고 있으니 통과"라며 곧바로 성공해버리는 버그가 생긴다.

#### hash_lock — PostgreSQL이 겪은 레이스를 InnoDB는 왜 안 겪나

[buf0buf.cc - buf_page_init_for_read](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L4960-L5005)
```cpp
// buf0buf.cc:4960-5005
buf_page_mutex_enter(block);
buf_page_init(buf_pool, page_id, page_size, block);

/* Note: We are using the hash_lock for protection. This is
safe because no other thread can lookup the block from the
page hashtable yet. */

block->mark_for_read_io();
buf_page_set_io_fix(bpage, BUF_IO_READ);
...
mutex_exit(&buf_pool->LRU_list_mutex);

rw_lock_x_lock_gen(&block->lock, BUF_IO_READ, UT_LOCATION_HERE);

rw_lock_x_unlock(hash_lock);   // ← X-lock을 다 잡은 뒤에야 hash_lock을 놓는다
buf_page_mutex_exit(block);
```

> InnoDB도 구조적으로는 PG16과 같다
> `io_fix`(별도 필드)와 `lock_word`(별도 필드)가 나뉘어 있다.
>  그런데도 PostgreSQL이 겪은 "IO 진행 여부 + 락 획득을 레이스 없이 동시에 확인하고 싶다"는 문제를 안 겪는 이유는, **페이지 해시 테이블을 보호하는 `hash_lock`을 `io_fix` 설정부터 X-lock 획득 완료까지 전 구간에 걸쳐 계속 쥐고 있기 때문**이다.
>  주석 그대로 "다른 어떤 스레드도 아직 이 블록을 해시 테이블에서 찾을 수 없으니 안전하다"
>  즉 다른 스레드는 X-lock이 완전히 걸린 뒤(`hash_lock` 해제 이후)에야 이 블록의 존재를 알게 되므로, "IO 중인지 + 락 잡을 수 있는지"를 동시에 물어볼 상황 자체가 생기지 않는다.
>  나중에 다른 스레드가 이 블록을 찾아오면, 이미 걸려있는 X-lock에 그냥 평범하게 대기(`os_event` 블로킹)하면 그만이다.

| | PostgreSQL | InnoDB |
|---|---|---|
| `io상태`/`lock상태` 분리 여부 | 분리(문제였음) | 분리(구조는 동일) |
| 이 분리가 실제 레이스를 만드나 | 만듦 → 그래서 하나로 통합(PG19) | **안 만듦** — `hash_lock`이 "블록 발견 가능 시점"을 X-lock 획득 이후로 미뤄버려서 애초에 동시 체크가 필요한 상황이 안 생김 |
| 해결 방식 | 원자적 워드 통합(설계 변경) | 기존에 쥐고 있던 더 굵은 락(`hash_lock`)으로 그 구간 전체를 덮음 |
| 트레이드오프 | 그 구간에서 굵은 락(파티션 락)을 안 써도 됨 → 더 세분화된 동시성 | `hash_lock`(해당 해시 파티션 전체)을 그 구간 내내 붙잡음 → 상대적으로 더 굵은 락 |

InnoDB는 진짜 AIO를 오래전부터 써왔지만, "레이스 프리 단일 CAS"로 문제를 풀지 않고 **이미 있던 굵은 락(`hash_lock`)에 기대는** 방식을 택했다. PostgreSQL이 PG19에서 원자적 워드까지 통합해가며 새로 설계한 건, 아마 이런 굵은 락을 오래 붙잡는 걸 피하고 싶어서였을 것으로 보인다.

### 블로킹 대기 — `os_event_t` (PG의 `PGSemaphoreLock`에 대응)

[sync0rw.h](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.h#L413-L419)
```cpp
os_event_t event;           // 일반 대기자 큐용
os_event_t wait_ex_event;   // "다음 X-lock 대기자" 전용 채널
```

CAS로 못 잡으면 `os_event_wait_low()`로 진짜 OS 레벨 블로킹에 들어간다
PostgreSQL의 `MyProc->sem`(`PGSemaphoreLock`/`PGSemaphoreUnlock`)에 정확히 대응하는 메커니즘이다.
`event`와 `wait_ex_event`를 분리해둔 이유는, 하나뿐이면 X-lock 대기자가 S-lock 하나 풀릴 때마다 불필요하게 깨어나는 걸 막기 위해서다
"다음 writer"만 별도 채널로 관리해서 필요한 대상만 정확히 깨운다.

### 락 오브젝트는 복사 불가

[sync0rw.h](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.h)
```cpp
rw_lock_t(const rw_lock_t &) = delete;
rw_lock_t &operator=(const rw_lock_t &) = delete;
```

락은 동일성(identity)이 핵심이라 복사를 허용하면 원본을 기다리던 대기자들과 복사본이 서로 무관해져 상호배제가 깨진다 — `std::mutex`가 복사 불가능한 것과 같은 이유. `std::atomic` 멤버 때문에 사실 자동으로도 막히지만, 의도를 명확히 하려고 명시적으로 `= delete`했다.

### 참고 자료

- [buf0buf.h (BufferDesc/buf_block_t 대응)](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/buf0buf.h)
- [sync0rw.h (rw_lock_t 선언)](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.h)
- [sync0rw.ic (CAS 루프 등 inline 구현)](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/sync0rw.ic)
- [sync0rw.cc (락 획득/대기 로직)](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/sync/sync0rw.cc)
- [PAGE_INNODB_LOCK_SYS (공식, Lock-sys)](https://dev.mysql.com/doc/dev/mysql-server/latest/PAGE_INNODB_LOCK_SYS.html)
