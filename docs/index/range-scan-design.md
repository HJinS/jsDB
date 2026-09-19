## Range Scan / Prefix 검색 설계

> [btree-latch-crabbing.md](btree-latch-crabbing.md)가 "여러 페이지를 순회할 때 락을 어떻게 넘기는가"를 다뤘다면, 이 문서는 "**어디서부터 어디까지, 어느 방향으로** 순회할 것인가"를 다룬다. GitHub 이슈 #41(조회 기능 개선)을 위한 설계 논의 정리.

### 개념 모델 — 정렬된 숫자선 위의 구간

전체 키 공간을 하나의 정렬된 숫자선이라고 보면, point search는 그 위의 한 점을 찾는 것이고 range/prefix 검색은 **연속된 구간**을 찾아 걷는 것이다. `col1=5`처럼 컬럼 일부만 고정하는 prefix 검색도, 인코딩이 byte-comparable(사전순 정렬 보존)하기 때문에 항상 하나의 연속된 구간으로 뭉쳐 있다는 게 보장된다 — 이게 이 설계 전체의 전제다.

### prefix 검색과 explicit range는 BTree 입장에서 같다

당초엔 "A: 경계 키 사전 계산(explicit range) / B: 매 항목 조건 검사(prefix 검색)"로 두 가지 방식을 구분했으나, 구분 자체가 잘못된 기준이었다.
BTree/Cursor 레벨에서 이 둘은 **완전히 동일한 메커니즘**(경계 bytes로 seek → step으로 순회 → 종료 조건 체크)이고, 유일한 차이는 "경계 bytes를 어떻게 만드는가"뿐이다.

진짜 분기 기준은 **prefix냐 range냐가 아니라 "이 경계가 open(exclusive)이냐 closed(inclusive)냐"**다. 아래 "boundary 생성 규칙" 절 참고.
실제로 `Table.selectByPrefix`는 `selectByRange`를 그대로 호출하는 얇은 wrapper로 구현되어 있다(양끝 bound를 같은 값+inclusive로 구성).

### 상한(successor) 계산

[MultiColumnKeySerializer.kt](../../src/main/kotlin/index/serializer/MultiColumnKeySerializer.kt) — `serializeUpper()`

```kotlin
// 주어진 prefix 컬럼들을 packKeyItem으로 인코딩한 뒤,
// 뒤에서부터 0xFF가 아닌 첫 byte를 찾아 +1, 그 뒤는 잘라낸다
for (idx in prefixBytes.indices.reversed()) {
    val unsigned = prefixBytes[idx].toInt() and 0xFF
    if (unsigned < 0xFF) {
        prefixBytes[idx] = (unsigned + 1).toByte()
        return prefixBytes.copyOfRange(0, idx + 1)
    }
}
return null  // 상한 없음 (unbounded)
```

컬럼 타입(Int/Long/String 등)에 상관없이 `escapeZeroBytes`가 모든 타입에 terminator(`0x00`)를 붙인다는 걸 확인했기 때문에, 타입별 분기 없이 **하나의 알고리즘**으로 전부 처리한다.

- **ASC 컬럼**: terminator가 `0x00`이라 보통 그 자리서 바로 +1 → 길이 불변
- **DESC 컬럼**: 전체가 반전돼 있어 terminator도 `0xFF` → 항상 carry 발생 → **결과가 원본보다 1byte 짧아짐**(terminator 자리가 통째로 잘림). 이 byte 배열은 순수 비교 전용이라 디코드될 일이 없으므로 문제되지 않는다
  - `serializeUpper`는 SQL 조건 -> byte로 가는 단방향으로만 쓰인다).

**상한이 없는 경우**: `descending=true`인 컬럼에 값으로 정확히 `NULL`을 줄 때만 발생한다(non-null 값은 flag/terminator 구조가 항상 여유를 보장하므로, 값이 타입의 최댓값이어도 무관).

- desc 컬럼의 NULL 그룹은 정책상 항상 물리적으로 트리 맨 끝에 위치하므로 "상한 없음"은 에러가 아니라 **"이 그룹이 트리 끝까지 이어진다"는 유효한 정보**라고 판단해 `ByteArray?`로 바꾸고 오른쪽 끝에서 탐색을 진행한다.

### boundary 생성 규칙 — serialize vs serializeUpper

`serializeUpper`(successor)가 필요한지 여부를 결정하는 건 컬럼 개수도, "prefix냐 range냐"도 아니라 **오직 "이 경계가 open이냐 closed냐"**다.
그리고 이 판단은 값이 실제로 존재하는지와도 무관하다.

- byte threshold 비교라, gap이 있는 데이터에서도 정확히 동작함(99/102만 있고 100이 없어도 `<=100` 판단이 정확함).

> `x <= v ⟺ x < successor(v)`. Cursor의 종료판단 comparator
>
> - FORWARD: `candidate >= boundary`면 멈춤
> - BACKWARD: `candidate < boundary`면 멈춤
> - open/closed: **어떤 boundary 생성 함수를 쓰느냐**로만 표현.

- `serialize(v)` = `Lo(v)`: v로 시작하는 가장 작은 실제 키(남은 컬럼은 최솟값 padding)
- `serializeUpper(v)` = `Hi(v)`: v 그룹 전체를 지나 바로 다음 지점(successor, null 가능)

**seek 위치**(스캔 시작점):

| direction | 기준 bound | inclusive           | exclusive           |
| --------- | ---------- | ------------------- | ------------------- |
| FORWARD   | 하한(L)    | `serialize(L)`      | `serializeUpper(L)` |
| BACKWARD  | 상한(U)    | `serializeUpper(U)` | `serialize(U)`      |

**종료 판단**(Cursor 밖, Table이 매 entry마다 체크):

| direction | 기준 bound | inclusive           | exclusive           |
| --------- | ---------- | ------------------- | ------------------- |
| FORWARD   | 상한(U)    | `serializeUpper(U)` | `serialize(U)`      |
| BACKWARD  | 하한(L)    | `serialize(L)`      | `serializeUpper(L)` |

두 표가 서로 정확히 뒤집힌 대칭이다

- `serializeUpper` 하나로 "inclusive 상한"과 "exclusive 하한 seek"를 둘 다 커버하므로 별도의 predecessor(-1) 연산은 필요 없다.

컬럼 4개짜리 인덱스에서 `col3<=100`(col4는 무조건)처럼 **explicit range도 뒤에 unconstrained 컬럼이 남으면 prefix 검색과 동일하게 successor가 필요**하다

> 경계 컬럼 뒤에 unconstrained 컬럼이 남아있는가"라는 조건도 사실 이 open/closed 규칙에 포함되는 특수 케이스일 뿐이다(뒤에 컬럼이 남는 채로 "그 그룹 전체를 포함"하고 싶다는 건 곧 inclusive의 한 형태).

이 판단(어떤 serialize 함수를 쓸지)은 **Table이 스캔 시작 전 딱 한 번** 내려서 `ByteArray`로 만들어 BTree에 넘긴다

- BTree/Cursor 내부에는 open/closed 개념도, 컬럼 개수 개념도 전혀 필요 없다.
- `Table.serializeBound`가 이 표(seek/종료판단 × inclusive/exclusive) 그대로를 구현한다.

### NULL 정렬 위치

| DB                   | NULL 취급                   | ASC   | DESC      |
| -------------------- | --------------------------- | ----- | --------- |
| PostgreSQL           | 최댓값                      | LAST  | FIRST     |
| MySQL / SQLite       | 최솟값                      | FIRST | LAST      |
| jsDB (수정 전, 버그) | 항상 `0x00`(desc 반전 누락) | FIRST | **FIRST** |
| jsDB (수정 후)       | 최솟값(MySQL/SQLite 컨벤션) | FIRST | LAST      |

[BaseKeySerializer.kt](../../src/main/kotlin/index/serializer/BaseKeySerializer.kt) `packKeyItem()`의 `if(key==null) return byteArrayOf(...)`가 desc 반전 단계보다 먼저 return돼서 asc/desc 상관없이 항상 `0x00`이던 버그를 `if(descending) 0xFF else 0x00`으로 수정.
`MultiColumnKeySerializer`가 prefix 생략 시 쓰는 패딩(desc면 `0xFF`)과도 일관성이 맞춰졌다.

### DESC 처리는 인코딩 단계에서 이미 끝남

`packKeyItem`이 desc 컬럼이면 `flag+content+terminator` 전체를 비트 반전해서 저장하므로, 그 이후 단계(BTree 순회, `serializeUpper`의 successor 계산 등)는 asc/desc를 전혀 몰라도 된다

- 항상 byte 증가 방향으로만 비교/순회하면 그게 곧 선언된 논리 순서다.
- 결과 정렬도 마찬가지 -> 쿼리 순서가 인덱스 선언과 정확히 일치하거나 정확히 전체 반대면 `next`/`prev` 선택만으로 충분하다.

> **여러 컬럼이 섞인 경우**
> equality로 고정된 컬럼은 정렬 판정에서 제외(값이 하나뿐이라 정렬에 영향 없음). 나머지(범위/자유 컬럼)가 전부 인덱스와 일치하거나 전부 반대면 단순 순회로 해결되고, 일부만 일치(섞임)하면 BTree/Table 레벨에서 불가능
>
> - 별도 인덱스 또는 인메모리 재정렬이 필요하며 이건 아직 없는 쿼리 플래너의 몫으로 스코프 아웃했다.
>   (참고: MySQL도 8.0 이전엔 이 문제를 filesort로 풀다가, 8.0에서 jsDB와 같은 "bit 반전 저장" 방식의 진짜 DESC 인덱스를 도입해 해결했다.) `Table.resolveScanDirection`이 이 판정을 구현한다.

### `findSearchPosition` — seek 위치 계산

[BTree.kt](../../src/main/kotlin/index/btree/BTree.kt)의 `findSearchPosition(key: ByteArray?, boundGiven: Boolean, direction, ...)`이 `Cursor`의 첫 `step()`이 읽을 위치를 계산한다.

세 갈래:

- **`boundGiven=false`**(이 방향의 seek 기준에 조건 자체가 없음): 비교할 key가 없으니 `findExtremeLeafPageId`로 곧장 leftmost/rightmost leaf로 내려간다.
- **`key==null`**(조건은 있었지만 `serializeUpper`가 successor를 못 만든 경우): BACKWARD는 그 끝 그룹 자체가 seek 대상이니 rightmost로. FORWARD는 "그보다 큰 것"이 존재하지 않으므로 결과가 아예 없음(`null` 리턴)
- **그 외**: `searchLeafNode(key)`로 "key 이상인 첫 슬롯"(lower-bound)을 찾는다.

> **`searchLeafNode` - lower-bound 검색**
> [SlottedPage.binarySearch](../../src/main/kotlin/storageEngine/page/SlottedPage.kt)는 표준 Java 스타일 - 못 찾으면 insertion point를 `-(low+1)`로 인코딩해서 리턴한다.
> `Node.search(key, exactIndex=true)`는 일치하면 `(그 위치, true)`, 안 하면 `(insertion point, false)`를 리턴하는데, 두 경우 다 "key 이상인 첫 슬롯"이라는 동일한 의미를 갖는다.

**`keyIdx` 보정**: seek boundary가 direction별로 이미 다르게 만들어져 있어서(위 표의 seek 열), 그 결과 해석도 달라진다.

| direction | seek boundary가 가리키는 지점           | keyIdx의 의미       | 보정                     |
| --------- | --------------------------------------- | ------------------- | ------------------------ |
| FORWARD   | 원하는 **첫** 항목 그 자체              | 이미 올바른 위치    | 없음(`idx = keyIdx`)     |
| BACKWARD  | 원하는 **마지막** 항목의 바로 다음 지점 | 정답보다 한 칸 앞섬 | `-1`(`idx = keyIdx - 1`) |

> 예시 (leaf = `[10,20,30,40,50]`)
> `col<=30`(BACKWARD, inclusive) -> boundary=`Hi(30)` -> keyIdx=3(값 40, insertion point) -> `-1`-> 슬롯2(값 30).
> `col<30`(BACKWARD, exclusive) -> boundary=`serialize(30)`=30 -> keyIdx=2(값 30과 정확히 일치, 제외 대상) -> `-1`-> 슬롯1(값 20).
> `isExist`가 true든 false든 결과는 항상 동일한 보정 - 존재 여부는 영향 없음.

### `Cursor.step()`/`findExtremeLeafPageId` 버그 두 건

**1. 트리 끝에 도달한 뒤 `step()`을 또 호출하면 죽음.**
`reachedEnd=true`가 되는 분기(마지막 slot이면서 이웃 페이지도 없는 경우)에서 마지막 값은 정상 리턴하지만, `currentPosition`이 갱신되지 않은 채로 남아있었다.
그 상태에서 lock은 이미 `closeAndRemoveLock`으로 지워졌는데, 다음 `step()` 호출이 `lockManager.last`를 그대로 다시 불러서 빈 `ArrayDeque`에서 `NoSuchElementException`이 났다.
-> 수정: `reachedEnd`일 때 `currentPosition = SearchPosition(INVALID_PAGE_ID, null)`로 명시적으로 표시하고, `step()` 맨 앞에 `if (currentPosition.pageId == INVALID_PAGE_ID) return null` 가드를 추가해서 그 상태를 실제로 체크하게 함.

**2. `findExtremeLeafPageId`가 leaf에 도달했을 때 같은 페이지를 중복으로 lock함.**
원래 코드는 `isLeaf`를 확인한 뒤에도 무조건 `nextPageId`(leaf의 경우 자기 자신)를 다시 `fetchPage`하고 `push`했다
leaf 하나에 대해 `PageLock` 객체가 두 개 쌓이는 셈.
-> 수정: `releaseAncestor` 호출은 그대로 두되 `if (isLeaf) break`를 재조회 코드보다 앞으로 옮겨서, leaf를 발견하면 그 즉시 멈추고 중복 fetch를 아예 안 하게 함.

### 비고

**호출자 책임 — `lowerBound`/`upperBound`에 equality를 양쪽 다 반영해야 함**: `col1=5 AND col2>=10`(col2 상한 없음)을 `lowerBound=Bound([5,10],true)`, `upperBound=null`로 표현하면, col1의 상한이 사라져서 `col1=6,7,8...`까지 스캔이 새어나간다(실제로 겪은 버그). equality로 고정하려는 leading 컬럼은 **반드시 양쪽 Bound에 같은 값으로** 들어가야 한다.

> **표현 가능한 조건의 구조적 한계 — range 컬럼 최대 1개**: byte-lexicographic 순서는 컬럼을 우선순위대로 비교하는 구조라, `Bound` 하나(연속된 byte 구간)로 표현되는 건 "equality 컬럼들 → range 컬럼 최대 1개 -> 그 뒤로는 조건 없음" 패턴뿐이다. `5<=col1<10 AND 10<col2<20`처럼 **두 컬럼에 독립적인 range**가 걸리면 (col1,col2) 평면의 직사각형이 되어버려서 연속 구간이 아니게 된다

**기타 버그들**

- `updateRow`가 secondary index의 값을 `primaryIndex.keySerializer`로 잘못 인코딩하던 것
  - `insertRow`/`extractData`가 기대하는 `handle.valueSerializer` 포맷과 달라서, update를 한 번이라도 거친 row는 `selectByIndex`에서 깨짐.
- `selectByRange`의 종료판단이 `result.add()` **이후에** 체크되어서, 경계를 막 넘은 항목이 결과에 한 번 포함된 뒤에야 멈추던 off-by-one.
- `Cursor`가 `.use{}`로 감싸지지 않아서, 종료판단으로 조기 break하거나 `extractData`가 예외를 던지면 lock이 안 풀리던 문제.

### 레이어링

- **Node/LeafNode/InternalNode**: 순수 `ByteArray`만 다룸. 컬럼/타입/asc-desc를 전혀 모름. 제네릭 없음.
- **`Cursor`**(`index.btree` 패키지): 순수 기계적 byte 이동만(`step(): Pair<ByteArray, ByteArray>?`). `AutoCloseable` + 명시적 `step()`으로 설계
  - `Iterator`/`for-in`이나 `Sequence`는 호출자가 중간에 순회를 멈춰도 정리(cleanup) 훅이 없어서 leaf 락이 새는 문제가 생길 수 있다.
  - `PageLock`/`LockManager`가 이미 이 프로젝트에서 쓰는 `AutoCloseable` + 명시적 `close()` 관용구를 그대로 따랐다.
- **`BTree`**: 제네릭 없음, `KeySerializer`/`ValueSerializer` 분리 진행. `insert`/`delete`/`update`/`search`/`traverse`/`search(key, direction, boundGiven)` 전부 `ByteArray`만 주고받는다. Node 계열과 동일한 원칙("이 계층은 타입도 컬럼도 모르고 바이트만 다룬다")으로 통일됨 — 원래는 BTree가 자신이 소유한 `keySerializer`로 도메인 타입 ↔ byte를 변환하는 "번역 경계" 역할이었으나, boundary 생성 시 `serialize`/`serializeUpper` 중 뭘 쓸지 결정하는 책임이 이미 Table로 넘어간 상태에서 BTree가 serializer를 들고 있는 게 책임 분배상 맞지 않다고 재평가됨.
- **`IndexHandle`**(`schema` 패키지): `metadata` + `btree` + 그 인덱스 전용 `keySerializer`/`valueSerializer`를 한 단위로 들고 있음. `CatalogManager`가 이미 자기 소유 BTree마다 전용 serializer를 옆에 끼고 있던 패턴과 동일.
- **`Table`**: 도메인 타입(`List<Any?>`, `Row`)만 주고받고, `IndexHandle`의 `keySerializer`/`valueSerializer`로 BTree 호출 전후 직렬화/역직렬화를 전담.
  - `Bound`/`ColumnOrder`(둘 다 `schema` 패키지)로 호출자와 open/closed·정렬 의도를 주고받고, `serializeBound`/`resolveEqualPrefixLen`/`resolveScanDirection`/`resolveIndex`/`extractData`로 그걸 BTree가 다룰 수 있는 형태로 변환한다.
  - `Cursor`는 `.use{}` 블록 안에서만 소비하고 밖으로 절대 노출하지 않는다.
  - 공개 리턴은 eager한 `List<Row>`로 끊어서(lazy `Sequence`를 그대로 노출하면 Cursor를 노출하는 것과 같은 락 위험이 재발한다) 락 문제를 Table 경계에서 완전히 격리한다.

### 스코프 아웃 (별도 이슈)

- range 컬럼이 2개 이상인 조건에 대한 post-filter 엔진(아래 스코프 아웃 참고) - 지금은 호출자가 직접 걸러내야 함
- 컬럼별로 인덱스 방향과 다르게 섞인 `ORDER BY` — 별도 인덱스 또는 인메모리 재정렬 필요
- "인덱스에 없는 where 조건 검색", 그리고 **range 컬럼이 2개 이상인 조건**(`5<col1<10 AND 10<col2<20` 같은) - 둘 다 range scan과 별개로 전체 스캔 + Condition/Predicate 평가 엔진이 필요(코드베이스에 해당 추상화 전무). 후자는 `selectByRange`가 첫 range 컬럼까지만 좁혀주고, 그 이후는 호출자가 직접 필터링하는 식으로 지금은 우회.
