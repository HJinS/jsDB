## SQLite pcache — 단일 뮤텍스, 페이지 콘텐츠 락은 없음

> [innodb-lock.md](../MySQL/innodb-lock.md), [content-lock.md](../PostgreSQL/content-lock.md)와 짝을 이루는 SQLite 절. 다만 결론부터 말하면, SQLite에는 그 둘에 해당하는 **"페이지 콘텐츠 전용 락"이 아예 없다** — 왜 없어도 되는지까지 정리한다.

### PGroup과 단일 뮤텍스

기본 설정에서는 프로세스 전체가 `PGroup` 하나(`static PGroup`, [`pcache1.c:225`](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L225))를 공유하고, LRU 리스트/해시 테이블 전체를 `pGroup->mutex` **단일 뮤텍스**로 보호한다.

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

### 이 뮤텍스가 지키는 건 메타데이터뿐이다

`pGroup->mutex`는 LRU 리스트 + 해시 테이블(페이지 찾기용)만 보호한다 — InnoDB의 `LRU_list_mutex`+`hash_lock`, PostgreSQL의 `buffer_strategy_lock`+`BufMappingLock`과 같은 역할이다. 근데 **InnoDB의 `rw_lock_t`나 PostgreSQL의 content lock에 해당하는, "페이지 하나의 바이트 내용을 지키는 전용 락"이 SQLite pcache 계층엔 없다.**

### 왜 없어도 되나 — 동시성을 파일/WAL 레벨에서 처리하기 때문

SQLite는 페이지 단위 동시성 자체를 요구하지 않는 설계다. 기본 threading mode에서는 하나의 DB 커넥션을 여러 스레드가 동시에 쓰는 걸 상정하지 않고(또는 "Serialized" 모드로 컴파일하면 커넥션 레벨 전체를 하나의 뮤텍스로 직렬화), 실제 동시성 제어(다른 프로세스/커넥션과의 경합)는 페이지 캐시가 아니라 **파일 락/WAL 레벨**(`SQLITE_LOCK_SHARED`/`RESERVED`/`PENDING`/`EXCLUSIVE`)에서 이뤄진다. 그러니 "이 프레임 하나를 S/X로 잠근다"는 개념 자체가 버퍼 캐시 계층에는 필요 없다 — 훨씬 상위 레벨에서 이미 정리되어 내려온다.

### 정리

| | InnoDB | PostgreSQL | SQLite |
|---|---|---|---|
| 메타데이터 락 | 뮤텍스 6종(세분화) | 파티션 락 + 전용 strategy 락 | 단일 `pthread_mutex_t` |
| 콘텐츠 락 | CAS 기반 `rw_lock_t`(S/SX/X) | CAS 기반 LWLock/전용 구현 | **없음** — 동시성은 파일/WAL 레벨에서 처리 |
| 구현 방식 | 커스텀 lock-free 알고리즘 | 커스텀 lock-free 알고리즘 | OS 뮤텍스 그대로 위임 |
| pin(refcount) | `buf_fix_count` (atomic 카운터) | pin refcount (atomic 카운터) | 리스트 멤버십(`pLruNext==0`)으로 판별 — 별도 카운터 없음 |

### 참고 자료

- [pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c)
- [mutex_unix.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/mutex_unix.c)
