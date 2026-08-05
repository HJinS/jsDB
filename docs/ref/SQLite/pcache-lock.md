## SQLite pcache — 단일 뮤텍스, 페이지 콘텐츠 락은 없음

> [innodb-lock.md](../MySQL/innodb-lock.md), [content-lock.md](../PostgreSQL/content-lock.md)와 짝을 이루는 SQLite 절. 다만 결론부터 말하면, SQLite에는 그 둘에 해당하는 **"페이지 콘텐츠 전용 락"이 아예 없다** — 왜 없어도 되는지까지 정리한다.

### PGroup과 단일 뮤텍스

`PGroup`은 두 모드로 동작한다
- mode-1(`PCache`마다 전용 `PGroup`, 뮤텍스 없음)과 mode-2(전역 `PGroup` 하나를 모든 `PCache`가 공유, 뮤텍스 필요).
- **일반적인 기본 사용(별도로 `sqlite3_config(SQLITE_CONFIG_PAGECACHE, ...)`로 정적 버퍼를 안 준 경우)에서는 오히려 mode-1이 기본값**이라, 이 경우 `PGroup.mutex`는 아예 `NULL`이다.
- mode-2(전역 `PGroup` 하나를 `pGroup->mutex` **단일 뮤텍스**로 보호)는 앱이 시작 시점에 정적 페이지캐시 버퍼를 설정했을 때, 혹은 스레드 안전(`bCoreMutex>0`)이 켜져 있을 때 켜진다.

[pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L704-L723)
```c
// pcache1.c:704-723
/*
** The pcache1.separateCache variable is true if each PCache has its own
** private PGroup (mode-1).  pcache1.separateCache is false if the single
** PGroup in pcache1.grp is used for all page caches (mode-2).
**   *  Use a unified cache in single-threaded applications that have
**      configured a start-time buffer ... with non-NULL pBuf argument.
**   *  Otherwise use separate caches (mode-1)
*/
#elif SQLITE_THREADSAFE
  pcache1.separateCache = sqlite3GlobalConfig.pPage==0
                          || sqlite3GlobalConfig.bCoreMutex>0;
```

`sqlite3GlobalConfig.pPage==0`(정적 버퍼 미설정, 아주 흔한 상황)이면 `separateCache`가 `true`가 되어 mode-1이 된다
- 즉 **뮤텍스 자체가 없는 경우가 오히려 흔하다.** `bCoreMutex`(스레드 안전 설정)는 mode 결정의 OR 조건 중 하나일 뿐, threading mode와 PGroup mode는 서로 다른 축이다(아래 참고).

[pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L253)
```c
// pcache1.c:253
#define pcache1EnterMutex(X) sqlite3_mutex_enter((X)->mutex)
#define pcache1LeaveMutex(X) sqlite3_mutex_leave((X)->mutex)
```

이 뮤텍스는 `SQLITE_MUTEX_STATIC_LRU`라는 정적 뮤텍스 슬롯을 가리킨다.

### 실체는 그냥 `pthread_mutex_t` — CAS도, S/X 구분도 없음

InnoDB(`rw_lock_t`, CAS 기반 S/SX/X)나 PostgreSQL(LWLock/전용 content lock, CAS 기반)과 근본적으로 다른 지점이다.

[mutex_unix.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/mutex_unix.c#L175-L228)
```c
// mutex_unix.c — 정적 뮤텍스 배열, 평범한 pthread_mutex_t
static sqlite3_mutex *pthreadMutexAlloc(int iType){
  static sqlite3_mutex aMutex[] = {
    { .m = SQLITE3_MUTEX_INITIALIZER(2) },
    { .m = SQLITE3_MUTEX_INITIALIZER(3) },
    ...
  };
  ...
  default: {
    p = &aMutex[iType-2].m;   // SQLITE_MUTEX_STATIC_LRU도 이 배열의 슬롯 중 하나
    break;
  }
  ...
}
```

`aMutex[]`는 그냥 `SQLITE3_MUTEX_INITIALIZER`로 초기화된 POSIX `pthread_mutex_t` 배열이다. **SQLite는 커스텀 CAS 재시도 루프를 전혀 짜지 않고, OS 뮤텍스(`pthread_mutex_lock`/`unlock`)에 그대로 위임한다.** InnoDB/PostgreSQL이 각자 lock-free 알고리즘을 직접 구현한 것과 대비된다.

### 이 뮤텍스가 지키는 건 메타데이터뿐이다 — 소스 주석에 필드까지 명시되어 있음

`PCache1` 구조체 정의 자체에 "이 필드는 PGroup mutex 없이는 건드리면 안 된다"는 게 명시적으로 적혀 있다.

[pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L175-L203)
```c
// pcache1.c:175-203
struct PCache1 {
  /* ... nMax may be modified at any time by a call to the
  ** pcache1Cachesize() method. The PGroup mutex must be held
  ** when accessing nMax. */
  PGroup *pGroup;
  ...
  unsigned int nMax;                  /* Configured "cache_size" value */

  /* Hash table of all pages. The following variables may only be accessed
  ** when the accessor is holding the PGroup mutex.
  */
  unsigned int nRecyclable;           /* Number of pages in the LRU list */
  unsigned int nPage;                 /* Total number of pages in apHash */
  unsigned int nHash;                 /* Number of slots in apHash[] */
  PgHdr1 **apHash;                    /* Hash table for fast lookup by key */
  PgHdr1 *pFree;                      /* List of unused pcache-local pages */
  void *pBulk;                        /* Bulk memory used by pcache-local */
};
```

즉 `pGroup->mutex`가 보호하는 건 **LRU 리스트(`nRecyclable`) + 해시 테이블(`nHash`/`apHash`) + free 리스트(`pFree`) + `nMax` 설정값**이다 — InnoDB의 `LRU_list_mutex`+`hash_lock`, PostgreSQL의 `buffer_strategy_lock`+`BufMappingLock`과 같은 역할이다. 근데 **InnoDB의 `rw_lock_t`나 PostgreSQL의 content lock에 해당하는, "페이지 하나의 바이트 내용을 지키는 전용 락"이 SQLite pcache 계층엔 없다.**

전역 `PCacheGlobal`에도 같은 구분이 있다 — 초기화 후 고정되는 값(`szSlot`/`nSlot`/`pStart`/`pEnd`/`nReserve`/`isInit`)은 뮤텍스가 필요 없고, 런타임에 계속 바뀌는 `nFreeSlot`/`pFree`만 뮤텍스가 필요하다고 주석에 명시되어 있다.

### 왜 애초에 뮤텍스가 필요한가 — 여러 PCache가 페이지를 서로 뺏어 쓰기 때문

[pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L136-L156)
```c
// pcache1.c:136-156
/* Each page cache (or PCache) belongs to a PGroup.  A PGroup is a set
** of one or more PCaches that are able to recycle each other's unpinned
** pages when they are under memory pressure.
**
**   (1)  Every PCache is the sole member of its own PGroup. ...
**        Mode 1 ... operates without a mutex, and is therefore often faster.
**   (2)  There is a single global PGroup that all PCaches are a member of.
**        Mode 2 requires a mutex in order to be threadsafe...
*/
```

여러 `PCache`가 메모리 압박 상황에서 **서로의 unpinned 페이지를 빼앗아 재사용**할 수 있게 하는 게 `PGroup`의 존재 이유고, 이걸 하려면(mode 2, 단일 전역 PGroup) 뮤텍스가 필요하다.
반대로 각 `PCache`가 자기 전용 `PGroup`을 갖는 mode 1은 **뮤텍스가 아예 없다**(`PGroup.mutex == NULL`)
"공유 자원을 여럿이 나눠 쓰려니 락이 필요해진다"는 지극히 평범한 이유이고, jsDB의 `globalLatch`가 존재하는 이유와도 같은 논리다.

### 왜 없어도 되나 — 동시성을 파일 레벨에서 처리하기 때문

SQLite는 페이지 단위 동시성 자체를 요구하지 않는 설계다. 실제 동시성 제어(다른 프로세스/커넥션과의 경합)는 페이지 캐시가 아니라 **Pager 모듈이 관리하는 파일 레벨 락**에서 이뤄진다. [File Locking And Concurrency In SQLite Version 3](https://sqlite.org/lockingv3.html)에 5단계 락 상태가 공식 문서화되어 있다.

> "The pager module effectively controls access for separate threads, or separate processes."

| 상태 | 의미 |
|---|---|
| `UNLOCKED` | "No locks are held on the database" — 읽기/쓰기 모두 불가 |
| `SHARED` | "The database may be read but not written" — 여러 프로세스가 동시에 읽기 가능 |
| `RESERVED` | "The process is planning on writing to the database file at some point in the future but that it is currently just reading" — 딱 하나만 존재 가능, 향후 쓰기 예약 |
| `PENDING` | "The process holding the lock wants to write to the database as soon as possible" — 기존 SHARED들이 다 풀릴 때까지 대기 |
| `EXCLUSIVE` | "Only one EXCLUSIVE lock is allowed on the file" — 실제 쓰기에 필요 |

읽기는 `UNLOCKED → SHARED`, 쓰기는 `SHARED → RESERVED → PENDING → EXCLUSIVE` 순서로 전이한다.
이게 InnoDB의 `rw_lock_t`나 PostgreSQL의 content lock이 하는 일(페이지 하나 단위의 S/X 잠금)을 **파일 전체 단위**로 대신한다
- 그래서 pcache 계층엔 "이 프레임 하나만 S/X로 잠근다"는 개념 자체가 필요 없다.

> **참고**: 이건 지난번 얘기한 SQLite의 threading mode(Single/Multi/Serialized, `bCoreMutex` 등 뮤텍스 서브시스템 활성화 여부)와는 또 다른 축이다
> threading mode는 "이 프로세스 안에서 여러 스레드가 SQLite API를 동시에 불러도 되는가"를 다루고, 여기 파일 락은 "여러 프로세스/커넥션이 같은 DB 파일에 동시 접근할 때의 조율"을 다룬다.
> 서로 다른 문제를 서로 다른 레벨에서 푸는 것이다.

### 참고 자료

- [pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c)
- [mutex_unix.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/mutex_unix.c)
