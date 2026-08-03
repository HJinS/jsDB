## SQLite — B-tree Page Layout

> MySQL/PostgreSQL과 달리, SQLite는 파일 포맷을 **공식적으로, 아주 상세히 문서화**해뒀다: [Database File Format](https://sqlite.org/fileformat2.html). 아래 내용은 전부 이 공식 문서 인용이다.

### 페이지 헤더 — 리프 8바이트 / 인테리어 12바이트

| 오프셋 | 크기 | 설명 |
|---|---|---|
| 0 | 1 | 페이지 타입 플래그(0x02/0x05/0x0a/0x0d) |
| 1 | 2 | 첫 freeblock의 오프셋(없으면 0) |
| 3 | 2 | 페이지의 셀(cell, = 레코드) 개수 |
| 5 | 2 | 셀 컨텐츠 영역의 시작 오프셋 |
| 7 | 1 | 셀 컨텐츠 영역 내 단편화된 여유 바이트 |
| 8 | 4 | (인테리어 페이지 전용) 우측 자식 포인터 |

### Cell Pointer Array — dense, PostgreSQL/jsDB와 같은 계열

> "The cell pointer array consists of K 2-byte integer offsets to the cell contents. The cell pointers are arranged in key order."

레코드(cell)마다 **2바이트 포인터가 정확히 하나씩** 있고 키 순서로 정렬되어 있다. 지난번 정리한 InnoDB의 sparse directory(4~8개 레코드당 슬롯 1개 + 연결리스트)와 정반대로, **PostgreSQL의 `ItemId`/jsDB의 슬롯과 같은 dense 구조**다.

> "SQLite strives to place cells as far toward the end of the b-tree page as it can, in order to leave space for future growth of the cell pointer array."

셀 컨텐츠 영역은 페이지 끝에서부터 역방향으로 자란다 — jsDB의 `freeSpaceEnd`, PostgreSQL의 `pd_upper`와 같은 패턴. 헤더+포인터 배열과 셀 컨텐츠 영역 사이의 빈 공간이 미할당(unallocated) 영역이다.

### Freeblock — 삭제된 셀 공간의 연결리스트

> "The first 2 bytes of a freeblock are a big-endian integer which is the offset in the b-tree page of the next freeblock in the chain, or zero if the freeblock is the last on the chain."

삭제된 셀이 남긴 빈 공간은 freeblock 연결리스트로 관리된다(최소 4바이트 필요, 1~3바이트짜리 단편은 헤더의 "단편화된 여유 바이트" 필드로 별도 추적). InnoDB의 `PAGE_FREE`(삭제 레코드 연결리스트)와 발상은 비슷하지만, InnoDB는 "레코드 슬롯 전체"를 재사용 대상으로 삼는 반면 SQLite는 "레코드가 있었던 자리(바이트 공간)"만 재사용 대상으로 삼는다는 차이가 있다.

### jsDB와 비교

jsDB의 `SlottedPage`(슬롯 = offset+length, 레코드당 1개, dense)는 InnoDB보다 **SQLite/PostgreSQL의 dense pointer array 계열**에 훨씬 가깝다. 다만 페이지 헤더 안에 `parentPageId`/형제 페이지 ID를 직접 두는 건([../MySQL/page.md](../MySQL/page.md) 참고) InnoDB의 `FIL_PAGE_PREV`/`NEXT` 방식과 더 닮아있어서, jsDB는 "SQLite/PostgreSQL식 dense 슬롯 + InnoDB식 공통 헤더에 형제 포인터 통합"을 조합한 형태다.
