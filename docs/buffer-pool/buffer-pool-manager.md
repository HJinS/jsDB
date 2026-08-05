## BufferPoolManager
> `DiskManager` 에서 `FileChannel`을 통해 읽은 데이터를 담아두는 곳을 `Frame`이라고 칭한다.
> `Frame`에서는 `ByteBuffer.allocateDirect`을 통해 데이터를 담아둔다.
> 이 `Frame`의 풀(pool)을 관리하는 역할을 이 `BufferPoolManager`가 하는 할이다.

> MySQL(InnoDB)/PostgreSQL/SQLite가 페이지 구조, lock, LRU, pin을 각각 어떻게 다루는지 정리하고, 이 프로젝트에서 선택한 구조에 대해서 이야기한다.
> 각 DB별 상세 내용은 `docs/ref/{엔진}/` 아래 별도 문서로 정리하였다.

MySQL, PostgreSQL, sqlite가 buffer pool을 동시성 환경에서 어떻게 보호하는지 정리하면 다음과 같다.

### MySQL(InnoDB) — 뮤텍스 분리

메타데이터는 뮤텍스 6종(`chunks_mutex`/`LRU_list_mutex`/`free_list_mutex`/`flush_state_mutex`/`zip_free`/`zip_hash`)으로 나눠 보호하고, 페이지 내용은 제어 블록마다 있는 CAS 기반 `rw_lock_t`(S/SX/X 3단계, 스레드 신원 추적, AIO용 `pass` 메커니즘)로 보호한다.
두 계층("짧은 메타데이터 뮤텍스" + "긴 프레임별 rw-lock")으로 나뉘어 있는 구조다.

- 뮤텍스 표, 메타데이터/콘텐츠 락 관계, CAS 상세 → **[innodb-lock.md](../ref/MySQL/innodb-lock.md)**
- 페이지/레코드 물리 구조(FIL_PAGE 헤더, sparse page directory, 레코드 연결리스트) → **[page.md](../ref/MySQL/page.md)**
- LRU(Midpoint Insertion) → **[lru.md](../ref/MySQL/lru.md)**

### PostgreSQL — 파티션 락 + Content Lock

Pin(참조 카운트)을 먼저 잡고, 페이지 내용은 content lock(버전에 따라 2~3단계)으로 보호한다.
메타데이터는 파티션 분할된 `BufMappingLock`(버퍼 태그→버퍼 매핑) + 전용 `buffer_strategy_lock`(victim 탐색)으로 InnoDB처럼 분리되어 있다.

흥미로운 점은 content lock이 항상 지금 모습이 아니었다는 것이다
> **PG16까지는 그냥 `LWLock`**(네이티브 모드 SHARE/EXCLUSIVE 2개뿐)이었고, **PostgreSQL 19**에서 `BufferDesc.state`(64bit 원자적 워드)에 패킹된 전용 구현으로 바뀌면서 `BUFFER_LOCK_SHARE_EXCLUSIVE`가 처음으로 진짜 3번째 모드가 됐다.
> AIO 도입 과정에서 "IO 진행 여부"와 "락을 잡을 수 있는지"를 레이스 없이 한 번의 CAS로 확인해야 할 필요성이 핵심 동기였다.

- 버퍼 접근 규칙, 메타데이터 락, 버전별 구조 차이, CAS/세마포어 상세, AIO 커밋 메시지 근거 → **[content-lock.md](../ref/PostgreSQL/content-lock.md)**
- 페이지/튜플 물리 구조(Page/Tuple Header, hint bit, heap vs index page) → **[page.md](../ref/PostgreSQL/page.md)**
- LRU(Clock-Sweep) → **[lru.md](../ref/PostgreSQL/lru.md)**

### sqlite(pcache1.c) — 단일 그룹 뮤텍스, 페이지 콘텐츠 락 없음

프로세스 전체가 `PGroup` 하나를 공유하고, LRU 리스트/해시 테이블을 **단일 뮤텍스**로 보호한다.
이 뮤텍스는 실제로 평범한 POSIX `pthread_mutex_t`다
> InnoDB/PostgreSQL처럼 커스텀 CAS 알고리즘을 짜지 않고 OS 뮤텍스에 그대로 위임한다.
> **InnoDB의 `rw_lock_t`, PostgreSQL의 content lock에 해당하는 "페이지 콘텐츠 전용 락" 자체가 없다**
> SQLite는 페이지 단위 동시성을 요구하지 않고, 실제 동시성 제어를 데이터베이스 파일/WAL 레벨 락으로 처리한다.

- 단일 뮤텍스 구조, 왜 콘텐츠 락이 없어도 되는지 → **[pcache-lock.md](../ref/SQLite/pcache-lock.md)**
- 페이지 물리 구조(B-tree 페이지 헤더, dense cell pointer array, freeblock) → **[page.md](../ref/SQLite/page.md)** — 공식 문서([Database File Format](https://sqlite.org/fileformat2.html))가 있는 유일한 엔진이다.
- LRU(pure LRU) → **[lru.md](../ref/SQLite/lru.md)**

### BufferPoolManager(`globalLatch`) 및 Frame(`ReentrantReadWriteLock`)

[BufferPoolManager.kt](../../src/main/kotlin/storageEngine/BufferPoolManager.kt), [Frame.kt](../../src/main/kotlin/storageEngine/page/Frame.kt), [PageLock.kt](../../src/main/kotlin/storageEngine/page/PageLock.kt)

두 계층으로 나뉜다.

- **메타데이터 락**: `globalLatch: ReentrantLock`(`BufferPoolManager.kt:53`) 하나가 `pageTable`, `freeList`, `replacer`(LRU) 전체를 보호한다.
  - InnoDB(뮤텍스 분리)나 PostgreSQL(파티션 락 + 전용 strategy 락)처럼 세분화되어 있지 않고, sqlite의 단일 `PGroup` 뮤텍스와 개념적으로 가장 가깝다.
- **콘텐츠 락**: `Frame.latch: ReentrantReadWriteLock`(`Frame.kt:15`)가 프레임별로 하나씩 있어 페이지 데이터를 보호한다.
  - read/write 2단계뿐이며, PostgreSQL의 share-exclusive(힌트비트 갱신용) 같은 중간 단계는 없다.
  - 대신 `PageLock.downgradeLock()`(`PageLock.kt:56`)으로 write → read 다운그레이드만 지원한다.
- **pin**: `Frame.pinCount: AtomicInteger`(`Frame.kt:16`)가 refcount 역할을 한다.
  - `unpinPage()`에서 카운트가 0이 되는 순간에만 `replacer.unpin(frameId)`를 호출해 LRU 리스트로 되돌린다(`BufferPoolManager.kt:190-205`).
  - InnoDB의 buffer-fix count, PostgreSQL의 pin refcount와 같은 개념이다.

#### `ReentrantLock`/`ReentrantReadWriteLock` — 실제로는 뭘로 구현되어 있나

`globalLatch`도, `Frame.latch`도 결국 JDK `AbstractQueuedSynchronizer`(AQS) 위에 얹혀 있다.
InnoDB `rw_lock_t`/PostgreSQL content lock과 같은 "CAS 우선, 실패 시에만 OS 블로킹" 2단 구조를 그대로 따른다.

- **빠른 경로**: `state`라는 `int` 필드에 대한 CAS 한 번(`tryAcquire`/`tryAcquireShared`)으로 끝난다.
  - 시스템 콜 없는 순수 유저스페이스 연산 - `rw_lock_t.lock_word` CAS, `BufferDesc.state` CAS와 동일한 발상이다.
- **느린 경로**: CAS 실패 시 대기 노드를 만들어 AQS 내부 큐에 건 뒤, 큐 맨 앞일 때만 잠깐 스핀(`Thread.onSpinWait()`)하고, 그래도 안 되면 `LockSupport.park(this)`에서 블로킹한다.
- read-lock(`acquireShared`)과 write-lock(`acquire`)은 내부적으로 같은 큐/파킹 로직을 공유하고, `shared` 플래그로만 갈린다.

| | InnoDB | PostgreSQL | jsDB(JDK) |
|---|---|---|---|
| CAS 대상 | `lock_word` | `BufferDesc.state` | AQS `state` |
| CAS 실패 시 블로킹 | `os_event_wait_low()` | `PGSemaphoreLock()` | `LockSupport.park()` |

**락 취득 순서** (`fetchPage()`, `BufferPoolManager.kt:66-138`)
1. `globalLatch` 획득 → pageTable 조회/갱신, `replacer.pin()`, `pinCount` 갱신까지만 짧게 수행하고 해제.
2. (필요시) `frame.latch.writeLock()`을 잡은 채로 디스크 I/O 수행
   1. 이 구간은 `globalLatch` 밖에서 진행되어 다른 페이지에 대한 `fetchPage`/`unpinPage`를 막지 않는다.
3. `lockMode`에 따라 read/write 락을 최종적으로 잡고 `PageLock`을 반환.

> 이 "짧은 전역 메타락 → 해제 → 긴 프레임락" 순서는 InnoDB(LRU_list_mutex는 짧게, `block->lock`은 IO 동안 길게)와 PostgreSQL(BufMappingLock은 짧게, content lock은 IO 동안 길게) 모두가 따르는 패턴과 같은 철학이다.
> `fetchPage()`가 디스크 읽기 전에 `frame.latch.writeLock()`을 미리 잡는 것도, InnoDB가 AIO 읽기 시 X-lock을 거는 것과 같은 이유다
> - 디스크에서 버퍼로 데이터를 채우는 것 자체가 버퍼 입장에선 "쓰기"라서, 그 구간엔 아무도(읽기조차) 못 들어오게 막아야 한다.

---

## 부록. 버퍼 풀의 스코프 — 서버/프로세스 전역 vs 커넥션별

지금까지는 "버퍼 풀 안에서 락을 어떻게 거냐"를 비교했다면, 이건 한 단계 더 위 질문이다
- **버퍼 풀 인스턴스 자체가 몇 개나 떠 있는가?** 커넥션(세션)마다 따로 있는지, 아니면 서버 프로세스 전체에 하나뿐인지는 엔진마다 다르다.

| | InnoDB | PostgreSQL | SQLite |
|---|---|---|---|
| 서버 모델 | 서버 프로세스 1개, 커넥션 = **스레드** | 서버가 커넥션마다 **프로세스**를 fork | 서버 자체가 없음 — 앱에 링크되는 **라이브러리** |
| 버퍼 풀 개수 | 서버 프로세스당 1개(전역) | 서버 인스턴스(cluster)당 1개(전역) | 기본: **커넥션(+ATTACH된 DB)마다 1개** |
| 여러 DB/스키마 간 공유 | 공유(같은 프로세스 메모리) | 공유 — `BufferTag`에 `dbOid`가 있어 여러 DB 페이지가 한 풀에 공존 | 공유 안 함(각자 독립) |
| 공유 메커니즘 | 같은 프로세스 주소공간 → 포인터 공유 | 명시적 OS 공유 메모리(`shared_buffers`, postmaster 시작 시 1회 할당) + 세마포어 기반 락 | 없음(기본) / opt-in shared-cache는 **같은 프로세스 내로 한정**, 프로세스 경계는 절대 못 넘음 |

**요지**
- **InnoDB/PostgreSQL**: "서버 프로세스(인스턴스) 하나 = 버퍼 풀 하나"가 원칙이다. PostgreSQL은 커넥션이 스레드가 아니라 별도 OS 프로세스인데도 공유 메모리로 억지로(?) 하나의 풀을 공유하게 만든 것이라, 그만큼 프로세스 간 동기화 프리미티브(세마포어)가 필요해진다 — [content-lock.md](../ref/PostgreSQL/content-lock.md)에서 다룬 CAS/세마포어 구조가 바로 이 대가다.
- **SQLite**: 애초에 "서버"라는 개념이 없어서 비교 축 자체가 다르다. 기본값은 커넥션마다(그리고 `ATTACH`된 DB마다) 독립된 `PCache`이고, 서로의 캐시 내용을 전혀 모른다. opt-in인 shared-cache mode조차 프로세스 경계를 못 넘는다 — 공식 문서가 "obsolete, discouraged"라고 명시할 정도로 권장되지 않는 기능이다([sharedcache.html](https://sqlite.org/sharedcache.html)). 여러 프로세스가 같은 파일을 건드릴 때의 정합성은 버퍼 풀 공유가 아니라 [pcache-lock.md](../ref/SQLite/pcache-lock.md)에서 다룬 파일 락 5단계(`UNLOCKED~EXCLUSIVE`, [lockingv3.html](https://sqlite.org/lockingv3.html))가 대신 처리한다.
- **jsDB**: `BufferPoolManager`가 프로세스 전역 싱글턴 하나다 — InnoDB/PostgreSQL과 같은 모델이고, SQLite식 "커넥션별 독립 캐시"와는 다른 철학이다.

**참고 자료**
- [PostgreSQL: shared_buffers 설정](https://www.postgresql.org/docs/current/runtime-config-resource.html#GUC-SHARED-BUFFERS)
- [PostgreSQL: Architecture (커넥션당 backend 프로세스 fork)](https://www.postgresql.org/docs/current/tutorial-arch.html)
- [MySQL: InnoDB Buffer Pool](https://dev.mysql.com/doc/refman/8.4/en/innodb-buffer-pool.html)
- [SQLite: Shared-Cache Mode(obsolete, 프로세스 경계 못 넘음)](https://sqlite.org/sharedcache.html)

---

## 종합 비교

### 페이지 구조

| | InnoDB | PostgreSQL | SQLite | jsDB |
|---|---|---|---|---|
| 헤더 계층 | 2겹(FIL_PAGE 공통 + PAGE_HEADER 인덱스 전용) | 1겹(`PageHeaderData`) | 1겹(리프 8B/인테리어 12B) | 1겹(`HEADER_SIZE=56`) |
| 슬롯/디렉토리 | **Sparse**(4~8개 레코드당 1개) + 레코드 연결리스트 | **Dense**(`ItemId`, 레코드당 1개) | **Dense**(cell pointer array, 레코드당 1개) | **Dense**(슬롯 배열, 레코드당 1개) |
| heap/index 구분 | 없음("모든 페이지는 index") | 있음(heap page ≠ index page 포맷) | 해당 없음(B-tree 리프에 직접 저장) | 없음(B+tree 리프에 직접 저장) |
| 형제 페이지 포인터 위치 | 공통 헤더(`FIL_PAGE_PREV/NEXT`) | special space(`BTPageOpaqueData`) | 별도 개념 없음(포인터 배열로 트리 구성) | 공통 헤더에 고정 필드 |
| 공식 문서 | 사실상 없음(예전 Internals Manual 폐기) | 있음(Database Page Layout) | **가장 상세함**(Database File Format) | — |

**장단점**
- **Sparse(InnoDB)**: directory 공간을 아낄 수 있고 삽입이 그룹 안에서는 `next` 포인터 갱신만으로 끝나지만, 탐색이 "이진탐색+짧은 선형탐색" 2단계라 상수 비용이 붙고 역방향 접근이 불가능하다.
- **Dense(PostgreSQL/SQLite/jsDB)**: 슬롯 배열 자체로 바로 이진탐색이 끝나 구현이 단순하지만, 슬롯이 레코드 수만큼 필요해 공간을 더 쓴다.
- **heap/index 통합(InnoDB/jsDB)**: 별도 heap 저장소가 없어 구조가 단순하고 clustered index로 지역성이 좋지만, secondary index 갱신 비용이 PK 변경에 민감해진다.
- **heap/index 분리(PostgreSQL)**: 인덱스가 가벼워지고(포인터만) 구조가 유연하지만, 별도 저장소 관리 오버헤드가 생긴다(bloat, VACUUM 필요 등).

> 구현 난이도를 고려하여, 별도의 힙을 두거나, InnoDb처럼 sparse의 형태를 두지 않고 SQLite와 비슷한 구조를 결정했다.

### Lock(콘텐츠 락)

| | InnoDB | PostgreSQL | SQLite | jsDB |
|---|---|---|---|---|
| 메타데이터 락 | 뮤텍스 6종 분리 | 파티션 `BufMappingLock` + 전용 `buffer_strategy_lock` | 단일 `pthread_mutex_t` | 단일 `globalLatch` |
| 콘텐츠 락 | CAS 기반 `rw_lock_t`(S/SX/X 3단계) | CAS 기반 LWLock/전용 구현(2~3단계, 버전별 상이) | **없음**(파일/WAL 레벨로 위임) | `ReentrantReadWriteLock`(read/write 2단계) |
| 구현 방식 | 커스텀 lock-free | 커스텀 lock-free | OS 뮤텍스 위임 | JDK 표준 락 |

**장단점**
- **세분화(InnoDB/PostgreSQL)**: 컨텐션을 잘게 분산해 고동시성에 유리하지만, 구현/디버깅 복잡도가 매우 높다(CAS 재시도 루프, 스레드 신원 추적, 재진입 처리 등).
- **단일 락(SQLite/jsDB)**: 구현이 단순하고 정합성 버그 낼 여지가 적지만, 메타데이터 접근이 몰리면 병목이 될 여지가 있다(단, 임계구역이 짧으면 실사용 영향은 제한적).
- **콘텐츠 락 없음(SQLite)**: 버퍼 계층 코드가 훨씬 단순해지지만, 페이지 단위 동시 접근을 지원 못 해 상위(파일/커넥션) 레벨에서 동시성을 희생한다.
- **2단계(jsDB) vs 3단계(InnoDB/PostgreSQL)**: 구현은 단순하지만 hint-bit류 최적화(읽기와 배타적이지 않은 쓰기)를 못 한다 — 지금 jsDB엔 그런 최적화 대상 자체가 없어서 당장은 문제가 안 된다.

> 구현 난이도를 고려하여 Lock 자체를 세부적으로 나누지는 않았다. 다만 SQLite처럼 파일 레벨단위로 관리하는 것도 어려움이 있어 보여 크게 2가지로 Lock을 가져가는 것으로 결정했다.

### LRU

| 항목 | InnoDB | PostgreSQL | SQLite | jsDB |
|---|---|---|---|---|
| 알고리즘 | Midpoint Insertion(segmented) | Clock-sweep(usage counter) | Pure LRU(단일 리스트) | Midpoint Insertion(segmented) |
| 자료구조 | 이중연결리스트 + `LRU_old` 포인터 | 원형 배열 + `nextVictimBuffer` | 원형 이중연결리스트 | 이중연결리스트 + `midPoint` 포인터 |
| 스캔 저항 | old/young 서브리스트 + 재접근 시간(`old_blocks_time`) 체크 | 별도의 소형 buffer ring 분리 | 없음 | old/young 서브리스트 + 재접근 시간(`lruOldBlocksTimeMs`) 체크 |
| 승격 조건 | old 영역에서 시간 조건 만족 후 재접근 시 young으로 이동 | pin될 때마다 usage counter 증가 | 없음(항상 head로 이동) | old 영역에서 시간 조건 만족 후 재접근 시 young으로 이동 |
| pinned 페이지 처리 | 리스트에 남고 스캔 시 skip | 배열에 남고 skip | **리스트에서 물리적 제거** | **리스트에서 물리적 제거** |

**장단점**
- **Midpoint Insertion(InnoDB/jsDB)**: old/young 서브리스트와 재접근 시간 체크로 read-ahead/풀스캔에 의한 캐시 오염을 잘 막지만, 구현이 세 방식 중 가장 복잡하다(비율 조정, 서브리스트 경계 유지).
- **Clock-sweep(PostgreSQL)**: 리스트 재배치 자체가 없어(원형 포인터 이동 + usage count 감쇠만) 가장 가볍지만, 진짜 LRU의 근사치일 뿐이고 스캔 저항은 별도의 buffer ring으로 따로 구현해야 한다.
- **Pure LRU(SQLite)**: 구현이 세 방식 중 가장 단순하지만, 스캔 저항 메커니즘이 아예 없어 read-ahead/풀스캔에 캐시 전체가 오염될 수 있다.
- **pinned 페이지를 리스트에 남기는 방식(InnoDB/PostgreSQL)**: evict 스캔 시 pinned 여부를 매번 확인(skip)해야 하는 대신, pin/unpin 자체는 리스트 연산이 없어 저렴하다.
- **pinned 페이지를 리스트에서 물리적으로 빼는 방식(SQLite/jsDB)**: evict 후보를 고를 때 pinned 여부를 확인할 필요가 없어 `removeOldest()`가 단순해지지만, pin/unpin이 호출될 때마다 리스트 연산이 추가로 발생한다.

> InnoDB의 Midpoint Insertion의 형식을 많이 빌렸다. 다만 pinnedPage 처리를 물리적으로 제거하는 방식으로 변경했다.(난이도 고려)
> 일반 LRU를 안쓴 이유는 `Midpoint Insertion` 알고리즘 자체가 복잡해 보이지 않았고, 학습 용도로 적합해 보였기 때문이다.

자세한 코드 딥다이브는 각 엔진별 [lru.md](../ref/MySQL/lru.md) 문서 참고.

### Pin

| | InnoDB | PostgreSQL | SQLite | jsDB |
|---|---|---|---|---|
| 자료형 | `buf_fix_count`(atomic counter) | pin refcount(atomic counter) | **없음** — 리스트 멤버십(`pLruNext==0`)으로 판별 | `AtomicInteger pinCount` |
| LRU와의 관계 | pin 중에도 LRU 리스트 멤버십 유지(skip만 함) | 배열 슬롯 유지(skip만 함) | pin=리스트에서 제거 그 자체 | pin=리스트에서 제거 그 자체 |

**장단점**
- **별도 카운터(InnoDB/PostgreSQL/jsDB)**: "몇 번 pin 됐는지"를 정확히 알 수 있어 중첩 pin에 안전하지만, 필드가 하나 더 필요하다.
- **리스트 멤버십으로 대체(SQLite)**: 별도 카운터가 필요 없어 자료구조가 가볍지만, "pin"이라는 상태가 곧 "리스트에 없음"과 동치라 표현력이 떨어진다(예: pin 카운트 자체를 조회할 방법이 없음).

> pinCount자체는 관리를 하지만, skip 하는 방식의 경우 난이도를 고려해서 제외하였다.

---

## 이 프로젝트에서는

- **페이지 구조**: dense 슬롯 배열(PostgreSQL/SQLite 계열)을 택했다. InnoDB의 sparse directory+연결리스트는 공간은 아끼지만 구현 복잡도(그룹 분할/병합, 2단계 탐색)가 이 프로젝트 규모에 비해 과했다.
  - 형제 페이지 포인터를 페이지 헤더에 직접 두는 건 InnoDB식을 따랐다
  - B-tree 전용 special space를 따로 만들 필요 없이 구조가 단순해진다.
- **Lock**: 단일 `globalLatch` + 프레임별 2단계 rw-lock을 택했다. InnoDB/PostgreSQL의 세분화된 lock-free 설계는 컨텐션 분산엔 유리하지만, 그만큼 CAS 재시도/스레드 신원 추적/재진입 처리 같은 복잡도를 감당해야 한다
  - 이 프로젝트 규모에서는 컨텐션보다 정합성과 구현 단순성이 우선이었다. sqlite의 단일 뮤텍스 설계와 같은 절충이다. 다만 sqlite와 달리 페이지 콘텐츠 락(`Frame.latch`)은 유지했다
  - SQLite처럼 상위(파일/커넥션) 레벨에서 동시성을 희생하는 대신, 페이지 단위 동시 읽기/쓰기를 지원하고 싶었기 때문이다.
- **LRU**: InnoDB의 Midpoint Insertion을 택했다. Clock-sweep(PostgreSQL) 대비 구현이 직관적이고, 순수 LRU(SQLite) 대비 read-ahead/풀스캔에 의한 캐시 오염을 막을 수 있기 때문이다.
- **Pin**: `AtomicInteger` 카운터(InnoDB/PostgreSQL식) + pin 시 LRU 리스트에서 물리적 제거(SQLite식)를 조합했다. 카운터가 있어야 중첩 pin을 정확히 추적할 수 있고, 리스트 제거를 병행하면 evict 후보를 고를 때 pinned 여부를 매번 skip 처리할 필요가 없어 `removeOldest()` 구현이 단순해진다.
