# jsDB

디스크 기반 스토리지 엔진과 SQL 실행 엔진을 처음부터 직접 구현해보는 학습용 데이터베이스 프로젝트입니다.

## Stack

Kotlin 2.1 · JVM 21 · Gradle 9 · Kotest / MockK / JUnit5 · Kover

## Status

| Layer                   | 컴포넌트                                                            | 상태                             | 문서                                       |
|-------------------------|-----------------------------------------------------------------|--------------------------------| ------------------------------------------ |
| Disk I/O                | `DiskManager`                                                   | 완료                             | [DiskManager](./docs/disk/disk-manager.md) |
| Buffer Pool             | `BufferPoolManager`, `Frame`, `MidpointLRUPolicy`               | 완료                             |
| Page                    | `SlottedPage`, `FreeSpaceManager`                               | 완료                             |
| Index                   | `BTree`(disk-based) + `LockManager` / `PageLock`(Latch Crabbing) | 완료                             |
| Index (in-memory)       | `index/btree/inMemory/*`                                        | 완료 (page 기반 index 구현 전 index)  |
| index value byte encode | `index/btree`, `index/util/encoder`, 'index/serializer'         | 예정                             |
| Storage                 | `StorageManager` (CRUD 인터페이스)                                   | 진행중                            |
| Encoding                | `index/serializer/*`, `index/util/Encoder` (Row/Value 바이트 인코딩)  | 진행중                            |
| Config                  | `SimpleConfig`, `IndexConfig`                                   | 진행중                            |
| SQL                     | Parser                                                          | 예정                             |
| SQL                     | Query Executor                                                  | 예정                             |
| Runtime                 | End-to-end 실행 (SQL → 결과)                                        | 예정 — 현재 `Main.kt`는 placeholder |


## 참고
- [docs 모음](./docs)

## 실행 / 테스트

```bash
./gradlew test               # Kotest + MockK 기반 단위 테스트
./gradlew koverHtmlReport     # 커버리지 리포트 (build/reports/kover)
```

SQL을 실제로 파싱·실행하는 엔드투엔드 경로는 아직 없고, `Main.kt`는 placeholder 상태입니다.
