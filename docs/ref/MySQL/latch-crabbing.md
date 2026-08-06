## InnoDB B-tree — mtr 기반 "일괄 보유 후 일괄 해제"

> [innodb-lock.md](./innodb-lock.md)가 버퍼 프레임 콘텐츠 락(`rw_lock_t`) 자체를 다뤘다면, 이 문서는 그 락들을 **B-tree 탐색/삽입/삭제 중에 어떤 순서로 잡고 놓는지**를 다룬다.

### 두 계층의 락 — `index->lock` + 페이지별 `rw_lock_t`

InnoDB는 페이지 latch(`rw_lock_t`) 위에, **인덱스 전체를 덮는 락 하나**(`dict_index_t::lock`)를 추가로 둔다.

- [dict0mem.h > dict_index_t > rw_lock_t lock](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/dict0mem.h#L1246)을 보면 정의된 lock 객체를 볼 수 있다.
  - 흥미로운 점은 이 lock의 경우 **트리의 상위 레벨**을 보호하는 것이다.
  - 각각 `LeafNode`의 보호는 각 `buffer`에 있는 `lock`을 통해 보호하고 있는 것으로 보인다.

```cpp
struct dict_index_t {
  /** id of the index */
  space_index_t id;

  /** memory heap */
  mem_heap_t *heap;

  /** index name */
  id_name_t name;

  /** table name */
  const char *table_name;

  /** back pointer to table */
  dict_table_t *table;
  ...

  /** read-write lock protecting the upper levels of the index tree */
  rw_lock_t lock;
  ...
}
```

[dict0dict.ic > dict_index_get_lock](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/include/dict0dict.ic#L681-L687)

```cpp
/** Gets the read-write lock of the index tree.
 @return read-write lock */
static inline rw_lock_t *dict_index_get_lock(
    dict_index_t *index) /*!< in: index */
{
  ut_ad(index);
  ut_ad(index->magic_n == DICT_INDEX_MAGIC_N);

  return (&(index->lock));
}
```

### 탐색 시에 잠금

> 탐색(구조를 바꾸지 않는 연산)은 `index->lock`을 S-latch로, **구조를 바꿀 수도 있는 연산**(`BTR_MODIFY_TREE`)은 X-latch로 잡는다.

#### **`BTR_MODIFY_TREE`(비관적)**

그 안에서도 3단계로 갈린다

| 조건                                                                                                                                                                                                                                        | `index->lock` | 이유                                                                                                                                                           |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| delete 의도 + history list가 `BTR_CUR_FINE_HISTORY_LENGTH` 초과 + pending read I/O 있음(Most of delete-intended operations are purging. Free blocks and read IO bandwidth should be prior for them, when the history list is glowing huge.) | **X**         | 이런 delete 대부분은 purge(오래된 undo/history 정리). history list가 이미 거대해졌다면 정리를 최우선으로 밀어붙여야 하니, 재시도 여지 없이 X로 확실하게 잡는다 |
| Spatial(R-Tree) 인덱스 + 비관적 삭제 가능성(X lock the if there is possibility of pessimistic delete on spatial index. As we could lock upward for the tree)                                                                                | **X**         | R-Tree는 삭제 시 일반 B-tree와 달리 **위쪽으로도 락이 전파**될 수 있어(MBR 재계산 등) 처음부터 X로 안전하게                                                    |
| 그 외 일반적인 pessimistic insert/delete                                                                                                                                                                                                    | **SX**        | S-lock 보유자(순수 검색 스레드)는 여전히 통과시키면서 다른 SX/X만 막는다 — "구조가 바뀔 수도 있다"는 의도만 예약해두는 정도                                    |

세 경우 모두 `upper_rw_latch = RW_X_LATCH` — 상위 페이지는 쓰기 가능 상태로 latch.

#### **`BTR_CONT_MODIFY_TREE`/`BTR_CONT_SEARCH_TREE`**

이전 탐색을 이어감

> 이름 그대로 이미 진행 중이던 탐색의 연속이라 `index->lock`을 새로 잡지 않고, X 또는 SX가 이미 잡혀 있어야 한다고 `ut_ad`로 단언만 한다.
> R-Tree의 split/merge용 부모 탐색일 때만 예외적으로 `upper_rw_latch = RW_X_LATCH`, 나머진 `RW_NO_LATCH`(이전 단계에서 확보한 걸 재사용).

#### **`default`(`BTR_SEARCH_LEAF` 등 낙관적 경로)**

read-only 모드면 아예 락 없음

`!srv_read_only_mode`가 진입 조건이다

- **서버가 read-only면 애초에 아무것도 안 바뀌니 index->lock 자체를 안 잡는다.**

| 조건                                                                                                                                                                                          | `index->lock`          | 이유                                                                                               |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------- | -------------------------------------------------------------------------------------------------- |
| `s_latch_by_caller`(호출자가 `BTR_ALREADY_S_LATCHED`로 이미 잡음)                                                                                                                             | 새로 안 잡음, assert만 | 병렬 읽기(parallel read) 시 다른 스레드가 이미 쥐고 있을 수 있음 — S는 공유 가능하니 재확보 불필요 |
| 일반 검색(`The BTR_ALREADY_S_LATCHED indicates that the index->lock has been taken either in RW_S_LATCH or RW_SX_LATCH mode. For parallel reads another thread can own the dict index lock.`) | **S**                  | 순수 탐색이니 읽기 락으로 충분                                                                     |
| `modify_external`(BLOB/fulltext 등 외부 페이지 수정 겸용 `BTR_MODIFY_EXTERNAL needs to be excluded`)                                                                                          | **SX**                 | 트리 구조는 안 바꾸지만 외부 페이지 쓰기가 얽히니 S보다 한 단계 강하게                             |

이 경우들은 `upper_rw_latch = RW_S_LATCH`(read-only 서버면 `RW_NO_LATCH`).

> **관통하는 기준**: 모든 분기가 결국 "이 탐색이 끝난 뒤 상위 레벨 구조를 바꿀 가능성이 얼마나 확실한가" 하나로 수렴한다
> 확실히 없으면 S, 있을 수도 있으면 SX(읽기는 여전히 허용), 거의 확실하면 처음부터 X로 재시도 비용을 피한다. 페이지 하나짜리 `rw_lock_t`든 인덱스 전체짜리 `dict_index_t::lock`이든 "확신의 정도에 따라 락 강도를 고른다"는 동일한 설계 철학이다.

[btr0cur.cc](https://github.com/mysql/mysql-server/blob/99960bf74fa919347e4f4e3ca47672f333d6e91f/storage/innobase/btr/btr0cur.cc#L824-L866)

```cpp
// btr0cur.cc — btr_cur_search_to_nth_level
switch (latch_mode) {
  case BTR_MODIFY_TREE:
    /* Most of delete-intended operations are purging.
    Free blocks and read IO bandwidth should be prior
    for them, when the history list is glowing huge. */
    if (lock_intention == BTR_INTENTION_DELETE &&
        trx_sys->rseg_history_len.load() > BTR_CUR_FINE_HISTORY_LENGTH &&
        buf_get_n_pending_read_ios()) {
      mtr_x_lock(dict_index_get_lock(index), mtr, UT_LOCATION_HERE);
    } else if (dict_index_is_spatial(index) &&
               lock_intention <= BTR_INTENTION_BOTH) {
      /* X lock the if there is possibility of
      pessimistic delete on spatial index. As we could
      lock upward for the tree */

      mtr_x_lock(dict_index_get_lock(index), mtr, UT_LOCATION_HERE);
    } else {
      mtr_sx_lock(dict_index_get_lock(index), mtr, UT_LOCATION_HERE);
    }
    upper_rw_latch = RW_X_LATCH;
    break;
  case BTR_CONT_MODIFY_TREE:
  case BTR_CONT_SEARCH_TREE:
    /* Do nothing */
    ut_ad(srv_read_only_mode ||
          mtr_memo_contains_flagged(mtr, dict_index_get_lock(index),
                                    MTR_MEMO_X_LOCK | MTR_MEMO_SX_LOCK));
    if (dict_index_is_spatial(index) && latch_mode == BTR_CONT_MODIFY_TREE) {
      /* If we are about to locating parent page for split
      and/or merge operation for R-Tree index, X latch
      the parent */
      upper_rw_latch = RW_X_LATCH;
    } else {
      upper_rw_latch = RW_NO_LATCH;
    }
    break;
  default:
    if (!srv_read_only_mode) {
      if (s_latch_by_caller) {
        /* The BTR_ALREADY_S_LATCHED indicates that the index->lock has been
        taken either in RW_S_LATCH or RW_SX_LATCH mode. For parallel reads
        another thread can own the dict index lock. */
        ut_ad(rw_lock_own_flagged(dict_index_get_lock(index),
                                  RW_LOCK_FLAG_S | RW_LOCK_FLAG_SX));

      } else if (!modify_external) {
        /* BTR_SEARCH_TREE is intended to be used with
        BTR_ALREADY_S_LATCHED */
        ut_ad(latch_mode != BTR_SEARCH_TREE);

        mtr_s_lock(dict_index_get_lock(index), mtr, UT_LOCATION_HERE);
      } else {
        /* BTR_MODIFY_EXTERNAL needs to be excluded */
        mtr_sx_lock(dict_index_get_lock(index), mtr, UT_LOCATION_HERE);
      }
      upper_rw_latch = RW_S_LATCH;
    } else {
      upper_rw_latch = RW_NO_LATCH;
    }
}
```

### 잠금 해제

#### 낙관적(optimistic) 경로 — 리프까지 다 내려간 뒤 한꺼번에 해제

`mtr_t`(mini-transaction)가 탐색 도중 잡은 모든 페이지 latch를 savepoint로 기록해둔다. `BTR_SEARCH_LEAF`/`BTR_MODIFY_LEAF`(리프만 건드리면 되는, 구조 변경 없는 연산)인 경우:

[btr0cur.cc](https://github.com/mysql/mysql-server/blob/99960bf74fa919347e4f4e3ca47672f333d6e91f/storage/innobase/btr/btr0cur.cc#L1140-L1175)

```cpp

if (height == 0) {
    if (rw_latch == RW_NO_LATCH) {
      latch_leaves = btr_cur_latch_leaves(block, page_id, page_size, latch_mode,
                                          cursor, mtr);
    }

    switch (latch_mode) {
      case BTR_MODIFY_TREE:
      case BTR_CONT_MODIFY_TREE:
      case BTR_CONT_SEARCH_TREE:
        break;
      default:
        if (!s_latch_by_caller && !srv_read_only_mode && !modify_external) {
          /* Release the tree s-latch */
          /* NOTE: BTR_MODIFY_EXTERNAL
          needs to keep tree sx-latch */
          mtr_release_s_latch_at_savepoint(mtr, savepoint,
                                           dict_index_get_lock(index));
        }

        /* release upper blocks */
        if (retrying_for_search_prev) {
          for (; prev_n_releases < prev_n_blocks; prev_n_releases++) {
            mtr_release_block_at_savepoint(
                mtr, prev_tree_savepoints[prev_n_releases],
                prev_tree_blocks[prev_n_releases]);
          }
        }

        for (; n_releases < n_blocks; n_releases++) {
          if (n_releases == 0 && modify_external) {
            /* keep latch of root page */
            ut_ad(mtr_memo_contains_flagged(
                mtr, tree_blocks[n_releases],
                MTR_MEMO_PAGE_SX_FIX | MTR_MEMO_PAGE_X_FIX));
            continue;
          }

          mtr_release_block_at_savepoint(mtr, tree_savepoints[n_releases],
                                         tree_blocks[n_releases]); // 조상 페이지 latch 전부 해제
        }
    }

    page_mode = mode;
  }
```

**중요한 점: 한 레벨 내려갈 때마다 즉시 부모를 놓는 게 아니다.**
루트부터 리프까지 내려가는 동안 조상 페이지 latch를 `tree_savepoints[]` 배열에 계속 쌓아두고, **리프에 도착한 뒤에야 한 번에 전부 해제**한다. "안전하니 부모를 지금 놓아도 된다"는 노드 단위 판단이 아니라, "리프까지 무사히 도착했으니 이번 연산은 구조 변경이 필요 없었다"는 전제 하에 배치로 처리하는 방식이다.

> **주의: 이 릴리즈 블록은 `level`이 아니라 `height == 0`(실제 물리적 리프)으로 게이트되어 있다.**
> `btr_cur_search_to_nth_level`은 늘 리프까지 내려가는 함수가 아니다.
>
> - `level` 파라미터로 임의의 레벨에서 멈출 수 있다.
> - 대표적으로 [btr0btr.cc의 `btr_page_get_father_node_ptr_func`](https://github.com/mysql/mysql-server/blob/99960bf74fa919347e4f4e3ca47672f333d6e91f/storage/innobase/btr/btr0btr.cc#L699)는 split/merge 때 부모의 downlink를 갱신하려고 `level + 1`(자식 바로 위 레벨)까지만 탐색한다

이렇게 `level != 0`으로 멈추는 호출은 `height`가 절대 0까지 안 내려가므로 위 릴리즈 블록 자체가 실행되지 않는다.
대신 이 호출은 `latch_mode`로 `BTR_CONT_MODIFY_TREE`/`BTR_CONT_SEARCH_TREE`를 넘기는데, 앞서 "락 종류를 가르는 기준"에서 본 것처럼 이 두 모드는 애초에 "Do nothing"(상위 mtr에서 이미 잡아둔 락을 그대로 이어받음)이라 조상 릴리즈 로직이 처음부터 관여할 필요가 없다

- "리프까지 안 갔으니 못 놓는다"가 아니라, "이미 조상 관리가 끝난 상태에서 한 레벨만 추가로 훑어보는" 별개의 용도이기 때문이다.

#### 비관적(pessimistic) 경로 — 애초에 안 놓는다

`BTR_MODIFY_TREE`(split/merge가 실제로 필요할 수 있는 삽입/삭제)는 `index->lock` X-latch부터 시작해서, 위 switch문에서 `break`로 아무것도 해제하지 않는다.
**조상 페이지 latch를 연산이 끝날 때까지 전부 쥐고 있는다.** split/merge가 실제로 위쪽까지 전파될 수 있으니, 처음부터 전체 경로를 X-latch로 확보해두고 시작하는 것이다.

호출부(`btr_cur_optimistic_insert` 등)는 보통 먼저 `BTR_MODIFY_LEAF`(낙관적)로 시도하고, 리프가 꽉 차서 split이 필요하다고 판단되면 **`BTR_MODIFY_TREE`로 처음부터 다시 탐색**하는 재시도 패턴을 쓴다

- "노드가 안전한지 내려가면서 검사"가 아니라 "일단 가볍게 시도하고, 안 되면 무겁게 다시"에 가깝다.

### 참고 자료

- [btr0cur.cc](https://github.com/mysql/mysql-server/blob/99960bf74fa919347e4f4e3ca47672f333d6e91f/storage/innobase/btr/btr0cur.cc) — `btr_cur_search_to_nth_level`
