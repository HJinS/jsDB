## PostgreSQL nbtree — Lehman & Yao, "coupling을 피하는" B-tree

> [content-lock.md](./content-lock.md)가 버퍼 콘텐츠 락 자체를 다뤘다면, 이 문서는 nbtree 모듈이 그 락을 B-tree 탐색 중 **어떤 순서로 잡고 놓는지**를 다룬다.
> PostgreSQL은 셋 중 유일하게, 클래식 latch crabbing을 의도적으로 피하도록 설계되어 있다.

### 알고리즘 자체가 다르다 — Lehman & Yao

PostgreSQL의 `nbtree`는 논문 원문을 그대로 구현한다고 명시한다.

[nbtree/README](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/README#L6-L9)

```
This directory contains a correct implementation of Lehman and Yao's
high-concurrency B-tree management algorithm (P. Lehman and S. Yao,
Efficient Locking for Concurrent Operations on B-Trees, ACM Transactions
on Database Systems, Vol 6, No. 4, December 1981, pp 650-670). We also
use a simplified version of the deletion logic described in Lanin and
Shasha (V. Lanin and D. Shasha, A Symmetric Concurrent B-Tree Algorithm,
Proceedings of 1986 Fall Joint Computer Conference, pp 380-389).
```

Lehman & Yao(L&Y)는 각 페이지에 **right-link**(오른쪽 형제 포인터)와 **high key**(그 페이지에 있을 수 있는 키의 상한)를 추가해서, "동시에 일어난 split을 사후에 감지"할 수 있게 만든 알고리즘이다.

- child로 내려간 뒤 high key와 찾는 키를 비교해서, 찾는 키가 high key보다 크면 "내가 내려오는 사이에 이 페이지가 split됐다"는 뜻이고, right-link를 따라가면 된다.

### 그래서 실제로는 "coupling을 안 한다"

[nbtree/README](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/README#L106-L110)

```
In most cases we release our lock and pin on a page before attempting
to acquire pin and lock on the page we are moving to.  In a few places
it is necessary to lock the next page before releasing the current one.
This is safe when moving right or up, but not when moving left or down
(else we'd create the possibility of deadlocks).
```

**하향 탐색(내려가는 경우) 기본값은 부모 락을 먼저 놓고 나서 자식 락을 잡는다**

- InnoDB/jsDB와 정반대다. 그 사이에 누가 페이지를 split해도 문제없는 이유가 바로 위 high key 체크: 락 없이 잠깐 무방비 상태로 있어도, 자식에 도착했을 때 "내가 아는 게 맞는 자식인가"를 high key로 검증하고, 아니면 그냥 오른쪽으로 이동해서 복구한다.
- **coupling(락을 겹쳐서 안전을 보장)이 아니라 detect-and-recover(사후 감지 후 복구)로 동시성을 푸는 것.**

### 왜 high key 비교만으로 충분한가 — "부모의 약속" + "자식이 먼저 바뀐다"

**1. 부모가 자식한테 준 약속이 있다.** 부모 페이지에서 인접한 두 키 `Ki`, `Ki+1` 사이를 보고 이 자식으로 내려왔다는 건, 부모가 "이 자식에 있는 값들은 전부 `Ki < v <= Ki+1`을 만족한다"고 약속한 것과 같다.

정상 상태에서 자식의 high key는 이 `Ki+1`과 같다.
그러니까 **아무 일도 안 일어났다면, 부모를 보고 내려온 이상 "찾는 키 ≤ 자식의 high key"는 항상 참이어야 한다.**
반대로 도착해서 확인해보니 `찾는 키 > 자식의 high key`라면? 부모의 약속이 깨진 거고, 그 사이에 뭔가 바뀌었다는 뜻이다

- 안 바뀌었다면애초에 이 자식이 아니라 그 오른쪽 자식으로 내려왔어야 정상이다. 그리고 그 "뭔가"는 split밖에 없다(다른 이유로는 한 페이지의 키 범위가 좁아지지 않는다).

[nbtree/README](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/README#L42-L49)

```
Lehman and Yao also require that the key range for a subtree S is
described by Ki < v <= Ki+1 where Ki and Ki+1 are the adjacent keys
in the parent page
```

> **2. 그런데 자식이 split되면, 부모는 그 사실을 바로 몰라도 된다.**
> split이 일어나면 **자식 쪽(새 형제 생성 + high key 갱신 + right-link 연결)이 먼저 원자적으로 끝나고, 부모에 downlink를 꽂는 건 완전히 별개의, 나중 단계**다.

심지어 크래시가 나서 **부모 downlink가 영영 안 생겨도** 탐색은 정상 동작한다고 못박혀 있다 — right-link만 따라가면 되니까.

> **두 가지를 합치면**: 탐색자는 "지금 부모가 최신 상태인지"를 아예 신경 쓸 필요가 없다. 부모를 본 시점엔 그 정보가 맞았고(1번), 그 뒤로 자식이 바뀌었다면 자식 자신의 high key가 즉시 그 사실을 반영하므로(2번), **결국 "찾는 키 vs 자식의 (항상 최신인) high key"라는 국소적 비교 하나로, 부모가 얼마나 오래됐든 상관없이 정확한 판단이 가능하다.**
> 부모한테 다시 물어볼 필요도, 부모 락을 오래 쥐고 있을 필요도 없는 이유가 바로 이것이다.

[nbtree/README](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/README#L633-L646)

```
An insertion that causes a page split is logged as a single WAL entry for
the changes occurring on the insertion's level ... followed by a second
WAL entry for the insertion on the parent level ...

Because splitting involves multiple atomic actions, it's possible that the
system crashes between splitting a page and inserting the downlink for the
new half to the parent.  After recovery, the downlink for the new page will
be missing.  The search algorithm works correctly, as the page will be found
by following the right-link from its left sibling ...
```

### 예외 — 삽입이 위로 전파될 때(ascent)는 오히려 coupling한다

split이 일어나 부모에 새 downlink를 꽂아야 할 때(상향 이동)는 얘기가 다르다.

부모의 downlink를 갱신하려고 위로 올라갈 때는 **child + 원래 parent + parent의 오른쪽 형제**, 최대 3개 락을 동시에 쥔다

- 이 지점이 PG에서 유일하게 "coupling"이라고 부를 만한 곳이다.
- 다만 여기서도 원래 parent 락은 형제 락을 잡기 전에 놓아버려서(같은 문단 뒷부분), 3개를 끝까지 다 겹쳐 쥐지는 않는다.

[nbtree/README](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/README#L139-L156)

```
Lehman and Yao must couple/chain locks as part of moving right when
relocating a child page's downlink during an ascent of the tree.  This is
the only point where Lehman and Yao have to simultaneously hold three
locks (a lock on the child, the original parent, and the original parent's
right sibling).  We don't need to couple internal page locks for pages on
the same level, though.  We match a child's block number to a downlink
from a pivot tuple one level up, whereas Lehman and Yao match on the
separator key associated with the downlink that was followed during the
initial descent.  We can release the lock on the original parent page
before acquiring a lock on its right sibling, since there is never any
need to deal with the case where the separator key that we must relocate
becomes the original parent's high key.  Lanin and Shasha don't couple
locks here either, though they also don't couple locks between levels
during ascents.  They are willing to "wait and try again" to avoid races.
Their algorithm is optimistic, which means that "an insertion holds no
more than one write lock at a time during its ascent".  We more or less
stick with Lehman and Yao's approach of conservatively coupling parent and
child locks when ascending the tree, since it's far simpler.
```

### 왜 PostgreSQL만 이렇게 설계했나 — 버퍼가 프로세스 간에 공유되기 때문

> 흥미로운 지점: L&Y 원 논문은 "읽기 락 자체가 필요 없다(각 백엔드가 페이지 복사본을 갖는다고 가정)"고 전제하는데, PostgreSQL은 [버퍼 풀이 여러 backend 프로세스에 공유](../../buffer-pool/buffer-pool-manager.md#버퍼-풀의-스코프--서버프로세스-전역-vs-커넥션별)되기 때문에 원문보다 더 보수적으로 읽기 락을 추가했다.
> 그럼에도 불구하고 "parent를 오래 쥐고 있는" coupling 자체는 여전히 최대한 피했다
>
> - high key/right-link라는 알고리즘적 장치로 락 보유 시간을 줄이는 게 핵심 목표였기 때문이다.

[nbtree/README](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/README#L63-L68)

```
Lehman and Yao don't require read locks, but assume that in-memory
copies of tree pages are unshared.  Postgres shares in-memory buffers
among backends.  As a result, we do page-level read locking on btree
pages in order to guarantee that no record is modified while we are
examining it.
```

### 참고 자료

- [nbtree/README](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/README)
- P. Lehman, S. Yao, "Efficient Locking for Concurrent Operations on B-Trees", ACM TODS Vol 6 No 4, 1981
