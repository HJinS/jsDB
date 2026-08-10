## SQLite B-tree — latch crabbing 자체가 없음

> [pcache-lock.md](./pcache-lock.md)에서 "페이지 콘텐츠 전용 락이 아예 없다"고 정리했던 것과 같은 이유로, SQLite의 `btree.c`에도 페이지 단위 latch crabbing이 없다. 왜 없어도 되는지를 코드로 확인한다.

### 거의 모든 B-tree API의 첫 줄 — `sqlite3BtreeEnter()`

[btmutex.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/btmutex.c#L71-L96)

```c
// btmutex.c:71-96
void sqlite3BtreeEnter(Btree *p){
  ...
  /* We should already hold a lock on the database connection */
  assert( sqlite3_mutex_held(p->db->mutex) );

  if( !p->sharable ) return;     // shared-cache가 아니면 여기서 끝 — 사실상 no-op
  p->wantToLock++;
  if( p->locked ) return;
  btreeLockCarefully(p);
}
```

두 가지가 눈에 띈다.

1. **함수 진입 시점에 이미 `db->mutex`(커넥션 전체를 지키는 뮤텍스)를 쥐고 있다고 가정한다.** 즉 B-tree 코드에 들어오기 한참 전, API 진입점에서부터 커넥션 단위로 이미 직렬화되어 있다.
2. **shared-cache가 아니면(`!p->sharable`) `BtShared` 락 로직 자체가 완전히 스킵된다.** [지난번 정리한 대로](./pcache-lock.md) shared-cache는 기본값이 아니고 공식적으로 obsolete 취급이라, 실제로는 이 함수가 거의 항상 즉시 리턴하는 셈이다.

`SQLITE_THREADSAFE=0`(싱글스레드 빌드)에서는 아예 이렇게 정의된다.

[btmutex.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/btmutex.c#L277-L279)

```c
void sqlite3BtreeEnter(Btree *p){
  p->pBt->db = p->db;
}
```

### 왜 필요 없나 — 애초에 같은 트리를 여러 스레드가 동시에 안 걷는다

InnoDB/PostgreSQL은 **여러 스레드/프로세스가 메모리 안의 같은 B-tree를 동시에 내려간다**는 전제 위에서 설계됐다. 그래서 "지금 이 페이지를 누가 바꾸고 있을 수도 있다"는 레이스를 페이지 단위 락으로 막아야 한다.

SQLite는 이 전제 자체가 성립하지 않는

- `db->mutex`(threading mode가 Serialized일 때) 또는 애플리케이션이 직접 보장하는 단일 스레드 사용(Multi-thread 모드)으로, **한 커넥션의 B-tree 코드는 한 번에 한 스레드만 실행**한다.
- 같은 트리를 두 스레드가 동시에 걷는 상황 자체가 없으니, "부모를 놓아도 안전한가"를 판단할 락 계층이 따로 필요 없다.

여러 프로세스/커넥션이 같은 파일을 건드리는 경우의 정합성은 B-tree 레벨이 아니라, 이미 [pcache-lock.md](./pcache-lock.md)에서 다룬 **파일 락 5단계**(`UNLOCKED~EXCLUSIVE`, [lockingv3.html](https://sqlite.org/lockingv3.html))로 처리된다

- 그마저도 페이지 단위가 아니라 파일 전체 단위다.

### 정리

|                       | InnoDB / PostgreSQL                                     | SQLite                                                            |
| --------------------- | ------------------------------------------------------- | ----------------------------------------------------------------- |
| 동시 접근 모델        | 여러 스레드/프로세스가 같은 메모리 B-tree를 동시에 순회 | 한 커넥션 = 한 번에 한 스레드만 B-tree 코드 실행                  |
| 페이지 단위 락        | 필요(그래서 crabbing/coupling 전략이 존재)              | **불필요**                                                        |
| 실제 동시성 제어 위치 | 버퍼 콘텐츠 락(페이지 단위)                             | 커넥션 뮤텍스(`db->mutex`, 트리 전체 단위) + 파일 락(프로세스 간) |

### 참고 자료

- [btmutex.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/btmutex.c)
