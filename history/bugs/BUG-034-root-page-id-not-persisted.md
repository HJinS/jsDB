# BUG-034 BTree rootPageId 미영속 — 재시작 시 root 위치 유실

- **커밋:** `ecec268`, `088b14e`, `7e4da7a`, `b7afe8f`, `139b94c`, `5412ec0`
- **날짜:** 2026-08-26 ~ 2026-09-01
- **컴포넌트:** `BTree.kt`, `MetaPageManager.kt`, `CatalogManager.kt`, `DataBase.kt`
- **상태:** 수정 완료 (재오픈 검증은 미완, 아래 "남은 사항" 참고)
- **출처:** `BTREE_IMPROVEMENT.md` §3.1, §5.2(2)

## 증상

`rootPageId`가 `BTree` 인스턴스의 메모리 변수로만 존재해서, 재시작하면 root 위치를 알 수 없어 인덱스를 다시 쓸 수 없었다. 또 root split이나 root 축소로 root가 바뀌어도 어디에도 기록되지 않았다.

## 원인

`BTree`는 `rootPageId`를 `-1`(빈 트리)로 시작하는 `var`로만 가지고 있었고, 저장소가 없었다. 카탈로그(system catalog)가 없던 시절에는 저장할 곳 자체가 없었다.

## 수정 (단계별)

| 커밋                 | 날짜       | 내용                                                                                                                             |
| -------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `ecec268`            | 2026-08-26 | `BTree` 생성자에 `onRootChanged: ((Long) -> Unit)?` 콜백 추가. root가 바뀔 때마다 호출 (예외 클래스 정리 커밋에 포함되어 들어감) |
| `088b14e`            | 2026-08-26 | `MetaPageManager` 추가. meta page(0번 페이지)에 table/index/column 카탈로그의 root page id와 next id를 저장                      |
| `7e4da7a`            | 2026-08-26 | 카탈로그별 root 변경 콜백을 `MetaPageManager.updateRootPageId`에 연결. 일반 인덱스의 root는 index catalog 행에 기록              |
| `b7afe8f`            | 2026-08-29 | 인덱스 생성 시 root 변경 콜백 연결, `CatalogManager.updateIndexRootPageId` 추가                                                  |
| `139b94c`            | 2026-08-31 | index/table load 시 카탈로그에 저장된 rootPageId로 `BTree` 복원                                                                  |
| `5412ec0`, `0f9122a` | 2026-09-01 | 빈 트리 표시를 `-1L` 리터럴에서 `INVALID_PAGE_ID` 상수로 통일                                                                    |

## 남은 사항

- 카탈로그와 meta page에 root를 기록하는 경로는 있지만, 닫았다 다시 여는 테스트가 없다.
- `DataBase.close()`는 `diskManager.close()`만 호출하고, buffer pool의 dirty page를 flush하지 않는다 (`BufferPoolManager`에 `flushAll`도 없음).
  - 따라서 close 시점에 dirty였던 meta page/카탈로그 페이지가 디스크에 반영된다는 보장이 코드상 확인되지 않는다.

## 관련

- BUG-022: 빈 트리(`rootPageId == -1`)에서의 search/traverse 동작
