## DiskManager

> 실제 Page들을 pageId 를 기반으로 정해진 disk에 read/write 하는 역할을 수행.
> 현재는 여러 논리 database 지원의 경우는 개발 범위에 포함 시키지 않음 따라서 설정 기준 단일 경로를 기준으로 사용하고 있음.

### FileChannel

[sqlite 에서 사용하고 있는 mmap](https://github.com/sqlite/sqlite/blob/5a9c01b01a360431249f4248ce59d7d9dff3c28c/src/pager.c#L5532) 과 유사하게 사용하기 위해 `FileChannel` 을 사용하고 있다.
다만 문법적으로 유사할 뿐이지, 세부적인 차이가 있다.
- memory-mapped 가 활성화 되어 있어야 mmap을 사용한다.

#### 다른 DB mmap
- [PostgreSQL의 mmap](https://github.com/postgres/postgres/blob/c12c101b0846b1e6488f2dc986a852fbc6bf2e3b/src/backend/storage/ipc/dsm_impl.c#L793)
- [mysql innodb mmap include](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/univ.i#L108)
  - [이곳](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/detail/ut/large_page_alloc-linux.h#L59) 을 보면 중앙 집중식으로 관리 함수가 있다기 보다는 필요한 곳에서 그때그때 사용하고 있다.


| 피처               | FileChannel(direct)                    | FileChannel(Heap)                          | MappedByteBuffer            | mmap                         |
|------------------|----------------------------------------|--------------------------------------------|-----------------------------|------------------------------|
| I/O 메커니즘         | 시스템 콜(read, write)                     | 시스템 콜(read, write)                         | 메모리 매핑(mmap)                | 메모리 매핑(mmap)                 |
| 메모리 할당           | off-heap direct memory                 | Jvm Heap                                   | off-heap virtual memory     | process virtual-memory       |
| 데이터 복사           | Disk -> Page Cache -> Direct Buffer(1회) | Disk -> Page Cache -> 임시 Direct -> Heap(2회) | Disk -> Page Cache(zero-copy) | Disk -> Page Cache(zero-copy) |
| File 사이즈 제한      | OS 한도까지 무제한                            | OS 한도까지 무제한                                | 단일 매핑 2GB 제한                | OS 한도까지 무제한                  |
| Thread Safety    | Safe                                   | Safe                                       | Not Thread-Safe             | Not Thread-Safe(직접 동기화)      |
| Resource Disposal | Channel: `close()` / Buffer: GC        | Deterministic(`close()`)                   | GC / cleaner 의존             | Deterministic(`munmap()`)    |
| GC 영향            | 받지 않음(JVM Heap 밖에 있음)                  | 받음                                         | 받지 않음                    | 해당 없음                        |
| Latency          | 시스템콜 + 복사 1회                           | 시스템콜 + 복사 2회                               | page fault 이후 메모리 속도        | page fault 이후 메모리 속도         |
| 제어권              | 높음                                     | 높음                                         | 낮음                          | 가장 높음                        |

이 프로젝트에서는 `MappedByteBuffer` 의 2GB의 사이즈 limit 과, 쓰레드 안정성을 위해 FileChannel(direct) 방식을 사용하고 있다.

추가 결정 사항
- 고정 크기의 `pageSize`
- 0번 페이지는 meta 페이지로 사용
