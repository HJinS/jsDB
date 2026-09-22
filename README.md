# jsDB

디스크 기반 스토리지 엔진과 SQL 실행 엔진을 처음부터 직접 구현해보는 학습용 데이터베이스 프로젝트입니다.

## Stack

Kotlin 2.4 · JDK 25 · Gradle 9 · Kotest / MockK / JUnit5 · Kover · Log4j2

## Status

| Layer                | 컴포넌트                                                                                         | 상태                                   | 문서                                                                                                     |
|----------------------|----------------------------------------------------------------------------------------------|--------------------------------------|--------------------------------------------------------------------------------------------------------|
| Disk I/O             | `DiskManager`                                                                                | 완료                                   | [DiskManager](./docs/disk/disk-manager.md)                                                             |
| Buffer Pool          | `BufferPoolManager`, `Frame`, `FrameNodePolicy` / `GenerationalList` (Midpoint LRU)          | 완료                                   | [Buffer Pool](./docs/buffer-pool/buffer-pool-manager.md)                                               |
| Page                 | `SlottedPage`, `FreeSpaceManager`                                                            | 완료                                   |                                                                                                        |
| Storage              | `StorageManager` (page 할당/조회/삭제, free list 연동), `MetaPageManager`                           | 완료                                   |                                                                                                        |
| Index                | `BTree`(disk-based, Latch Crabbing: `LockManager` / `PageLock`)                              | 완료                                   | [latch crabbing](./docs/index/btree-latch-crabbing.md)                                                 |
| Index (scan)         | `Cursor`, 범위 검색 / prefix 검색 (`BTree.search(key, direction, boundGiven)`)                     | 완료                                   | [range scan 설계](./docs/index/range-scan-design.md)                                                     |
| Index (in-memory)    | `index/btree/inMemory/*`                                                                     | 완료 (page 기반 구현 이전 버전, 참고용)          |                                                                                                        |
| Encoding             | `index/serializer/*` (byte-comparable 복합 키, ASC/DESC, NULL, `serializeUpper`), `BinaryRowSerializer` | 완료                                   | [encoding](./docs/encoding/key-value-encoding.md), [KEY-ENCODING-STORY](./history/KEY-ENCODING-STORY.md) |
| Catalog              | `CatalogManager` (table / index / column 시스템 카탈로그)                                          | 완료                                   |                                                                                                        |
| Table / DataBase     | `DataBase`(create/load table·index), `Table`(insert / select / update / delete, `selectByRange`, `selectByPrefix`) | 완료 (DDL은 create 계열만)                |                                                                                                        |
| Config               | `SimpleConfig`, `ConfigLoader`, `IndexConfig`                                                | 완료                                   |                                                                                                        |
| Schema (나머지 DDL)     | `DROP TABLE`, `DROP INDEX`, `ADD COLUMN`, `DROP COLUMN`                                      | 예정 ([#40](https://github.com/HJinS/jsDB/issues/40))          |                                                                                                        |
| Concurrency          | catalog / DataBase 동시성                                                                       | 예정 ([#42](https://github.com/HJinS/jsDB/issues/42))          |                                                                                                        |
| SQL                  | Parser                                                                                       | 예정                                   |                                                                                                        |
| SQL                  | Query Planner / Executor                                                                     | 예정                                   |                                                                                                        |
| Runtime              | End-to-end 실행 (SQL → 결과)                                                                     | 예정 — 현재 `Main.kt`는 placeholder       |                                                                                                        |

## 범위

- **포함**: 스토리지 엔진(page / buffer pool / B+Tree), 시스템 카탈로그, 테이블·인덱스 API, SQL 파서와 실행기.
- **제외 (현재 계획에 없음)**: 트랜잭션, WAL, crash recovery, checkpoint.
  - 따라서 비정상 종료 시의 일관성은 보장하지 않습니다.

## 알려진 한계

- `DataBase.close()`는 파일만 닫으며, buffer pool의 dirty page를 flush하지 않습니다. 재시작 후 데이터 유지는 아직 검증되지 않았습니다.
- 인덱스 하나로 표현할 수 있는 range 조건은 "equality 컬럼들 + range 컬럼 최대 1개"입니다. 그 밖의 조건은 호출자가 후처리해야 합니다.
- 정렬 방향이 컬럼마다 섞인 `ORDER BY`는 지원하지 않습니다.
- `CatalogManager.getIndexes`는 index catalog 전체를 훑습니다.

## 참고

- [docs 모음](./docs)
- [버그 히스토리](./history/bugs): 개발 중 만난 버그의 원인과 수정 기록

## 실행 / 테스트

```bash
./gradlew test               # Kotest + MockK 기반 단위 테스트
./gradlew koverHtmlReport     # 커버리지 리포트 (build/reports/kover)
```

SQL을 실제로 파싱·실행하는 엔드투엔드 경로는 아직 없고, `Main.kt`는 placeholder 상태입니다.
