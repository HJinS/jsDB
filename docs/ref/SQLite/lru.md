## SQLite — Pure LRU (`pcache1.c`)

기본 페이지 캐시 구현인 [`pcache1.c`](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c)는 InnoDB/PostgreSQL과 달리 세그먼트나 usage counter 없이 **순수 LRU**(단일 원형 이중연결리스트)를 사용한다.

- `PgHdr1`(`pcache1.c:117`)이 `pLruNext`/`pLruPrev`로 LRU 리스트를 구성한다.
- pinned 여부는 별도 플래그가 아니라 **리스트 멤버십 자체**로 판별한다.

[pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L133-L134)
```c
// pcache1.c:133-134
#define PAGE_IS_PINNED(p)    ((p)->pLruNext==0)
#define PAGE_IS_UNPINNED(p)  ((p)->pLruNext!=0)
```

- `pcache1PinPage()`(`pcache1.c:578`)는 페이지를 pin할 때 LRU 리스트에서 **물리적으로 제거**한다(`pLruNext = 0`).

[pcache1.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/pcache1.c#L583-L586)
```c
// pcache1.c:583-586
pPage->pLruPrev->pLruNext = pPage->pLruNext;
pPage->pLruNext->pLruPrev = pPage->pLruPrev;
pPage->pLruNext = 0;
```

- `pcache1Unpin()`(`pcache1.c:1078`)은 unpin 시 리스트 head(`pGroup->lru` 바로 뒤)에 다시 삽입한다.
- victim 선택은 `pcache1FetchStage2()`(`pcache1.c:874`) 안에서 `pGroup->lru.pLruPrev`, 즉 **리스트 tail(가장 오래 unpinned 상태였던 페이지)** 을 그대로 재사용한다 — old/young 구분도, 재접근 시간 체크도 없다.

### 핵심 포인트

- InnoDB/PostgreSQL과 달리 스캔 저항(scan resistance) 메커니즘이 아예 없다. read-ahead나 풀스캔이 캐시 전체를 오염시킬 수 있다는 뜻이다.
- pin 시 리스트에서 물리적으로 제거하는 방식은 jsDB의 `MidpointLRUPolicy.pin()`과 정확히 같은 패턴이다 — jsDB는 이 부분을 SQLite에서, old/young 세그먼트 구조는 InnoDB에서 각각 가져와 조합했다. 자세한 내용은 [../../buffer-pool/lru.md](../../buffer-pool/lru.md) 참고.
