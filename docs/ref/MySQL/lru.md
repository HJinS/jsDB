## MySQL(InnoDB) — Midpoint Insertion

**[MySQL BufferPool](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0buf.cc#L135)**
- 여기에는 BufferPool에 관련된 전체적인 설명이 적혀있다. 다만 여기에 LRU 관련 세부적인 내용은 적혀있지 않다.
- `Pointer-Swizzling`이라는 기법이 나오는데, 복잡도가 너무 증가하여 해당 프로젝트에서는 사용하지 않았다.

**[MySQL BufferPool LRU 구현](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0lru.cc#L61)**
- 여기에는 MySQL의 BufferPool LRU 관련 구현이 있으나 이것만으로는 전체적인 MySQL LRU의 동작 과정을 파악하기 어려워 다음의 문서를 참고했다.
- [MySQL BufferPool Scan Resistant](https://dev.mysql.com/doc/refman/9.7/en/innodb-performance-midpoint_insertion.html)
    - BufferPool의 scan 저항에 관련하여 적혀있다. 주로 innodb_old_blocks_pct와 innodb_old_blocks_time 에 대해서 다루고 있다.
- [MySQL BufferPool](https://dev.mysql.com/doc/refman/9.1/en/innodb-buffer-pool.html)
    - MySQL의 LRU - Midpoint Insertion에 대해서 이야기 하고 있다.
    - Midpoint Insertion이란 기본적으로 young(5/8), old(3/8) 의 비율을 가지고, 페이지를 읽을 경우 Midpoint(Old List의 가장 앞) 에 삽입하는것을 의미한다.
    - Old 영역에 있는 페이지를 처음 접근 한 경우 그 페이지를 Young 여역으로 옮긴다.
        - 이는 read-ahead 오퍼레이션이나 table-scan 같은 오퍼레이션이 기존의 캐싱에 영향을 끼치지 않도록 한다.
- 자세한 코드는 이 부분을 보면 알 수 있다.
    - [midpoint insert 코드](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0lru.cc#L862)
    - 특이점은 처음에는 1개의 리스트로 시작(무조건 young 취급) 하다가 특정 길이에 도달 할 경우 old Sublist를 만든다는 점이다.

[buf0lru.cc](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/buf/buf0lru.cc#L862)
```cpp
static inline void buf_LRU_add_block_low(buf_page_t *bpage, bool old) {
  ...
  if (!old || (UT_LIST_GET_LEN(buf_pool->LRU) < BUF_LRU_OLD_MIN_LEN)) {
    UT_LIST_ADD_FIRST(buf_pool->LRU, bpage);

    bpage->freed_page_clock = buf_pool->freed_page_clock;
  } else {
#ifdef UNIV_LRU_DEBUG
    /* buf_pool->LRU_old must be the first item in the LRU list
    /* midpoint insert 부분*/
    whose "old" flag is set. */
    ut_a(buf_pool->LRU_old->old);
    ut_a(!UT_LIST_GET_PREV(LRU, buf_pool->LRU_old) ||
         !UT_LIST_GET_PREV(LRU, buf_pool->LRU_old)->old);
    ut_a(!UT_LIST_GET_NEXT(LRU, buf_pool->LRU_old) ||
         UT_LIST_GET_NEXT(LRU, buf_pool->LRU_old)->old);
#endif /* UNIV_LRU_DEBUG */
    UT_LIST_INSERT_AFTER(buf_pool->LRU, buf_pool->LRU_old, bpage);

    buf_pool->LRU_old_len++;
  }
  ...
  /* midpoint insert 후에 old 플래그 설정 및 필요시 비율 조정 */
  if (UT_LIST_GET_LEN(buf_pool->LRU) > BUF_LRU_OLD_MIN_LEN) {
    ut_ad(buf_pool->LRU_old);

    /* Adjust the length of the old block list if necessary */

    buf_page_set_old(bpage, old);
    buf_LRU_old_adjust_len(buf_pool);

  } else if (UT_LIST_GET_LEN(buf_pool->LRU) == BUF_LRU_OLD_MIN_LEN) {
    /* The LRU list is now long enough for LRU_old to become
    defined: init it */

    buf_LRU_old_init(buf_pool); // Midpoint 초기화
  } else {
    buf_page_set_old(bpage, buf_pool->LRU_old != nullptr);
  }
}
```

**핵심 포인트**
- `LRU_old`가 old/young 서브리스트의 경계(midpoint)를 가리키는 포인터다.
- 새로 읽은 페이지는 `LRU_old` 바로 뒤(=old 영역의 head)에 삽입된다. 리스트 맨 앞(hot)을 곧바로 차지하지 못한다.
- old 영역의 페이지가 `innodb_old_blocks_time`(ms) 이상 지난 뒤 재접근되면 young으로 승격된다. 너무 빨리 재접근되면(같은 read-ahead/스캔 안에서) 승격시키지 않아 스캔 저항(scan resistance)을 갖는다.
- `buf_LRU_old_adjust_len()`이 블록 추가/제거마다 `LRU_old` 위치를 `innodb_old_blocks_pct`(기본 37%) ± tolerance 안으로 재조정한다.
- pin(버퍼 고정, buffer-fix count > 0)된 페이지도 **리스트에는 그대로 남아있고**, evict 스캔 시점에 건너뛴다. 리스트에서 물리적으로 빠지지 않는다.