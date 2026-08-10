## Key/Value 바이트 인코딩

> 여기서는 키 값을 어떤식으로 저장하고, `binarySearch`로 어떻게 대/소 를 비교하는지에 대한 내용이다.

- InnoDB(memcmp 기반, 부호 비트 반전) → **[ref/MySQL/encoding.md](../ref/MySQL/encoding.md)**
- PostgreSQL(타입별 비교 함수, 인코딩 트릭 불필요) → **[ref/PostgreSQL/encoding.md](../ref/PostgreSQL/encoding.md)**
- SQLite(serial type + varint, 역시 타입별 비교) → **[ref/SQLite/encoding.md](../ref/SQLite/encoding.md)**

### "비교를 바이트로 하는가, 타입으로 하는가"

세 엔진을 조사해보면 결국 질문 하나로 수렴한다: **B-tree가 두 키의 순서를 판단할 때, 저장된 바이트를 그대로 비교(`memcmp`)하는가, 아니면 저장된 바이트를 원래 타입으로 되돌린 뒤 그 타입의 비교 규칙을 따르는가.**

- **memcmp 방식 InnoDB**: 비교 자체는 아주 빠르지만(바이트 배열 비교 한 번), 그 대가로 **저장 시점에 값을 "바이트 순서 = 값 순서"가 되도록 미리 가공**해야 한다. 부호 있는 정수를 그냥 2의 보수로 저장하면 음수가 양수보다 바이트값이 커서 순서가 깨지므로, 부호 비트를 반전시키는 트릭이 필요하다.
- **타입별 비교 방식(PostgreSQL, SQLite)**: 저장 형식은 그 타입의 자연스러운 표현이면 충분하지만(정수는 그냥 정수), **비교할 때마다 타입에 맞는 비교 로직을 호출**해야 하니 상대적으로 비용이 크다.

### 결정 - memcmp, InnoDB와 비슷한 방법 사용

[Encoder.kt](../../src/main/kotlin/index/util/Encoder.kt), [BaseKeySerializer.kt](../../src/main/kotlin/index/serializer/BaseKeySerializer.kt)

**정수/실수 — 부호 비트 반전**

```kotlin
// Encoder.kt
fun Int.encodeSortable(): ByteArray {
    val sortableBits = this xor Int.MIN_VALUE      // 최상위 비트만 반전되는 효과
    return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(sortableBits).array().let(::escapeZeroBytes)
}
```

`Int.MIN_VALUE`는 최상위 비트만 1이고 나머지는 0이라, `xor`하면 결과적으로 [InnoDB의 `*buf ^= 128`](../ref/MySQL/encoding.md)과 동일한 효과(최상위 비트 반전)를 낸다.
부동소수점은 한 단계 더 있다

- 양수는 부호 비트만 반전하지만, **음수는 전체 비트를 반전**해야 한다(IEEE 754는 음수일수록 지수/가수부 비트 패턴이 "더 큰 값"처럼 보이는 구조라, 부호 비트만 뒤집으면 음수끼리의 순서가 거꾸로 됨).

**가변 길이 값 — 0x00 escape + terminator**

```kotlin
// Encoder.kt — escapeZeroBytes
// 0x00 → 0x00 0xFF 로 escape, 끝에 0x00 terminator 추가
```

길이를 별도로 저장하는 대신, 값 안의 `0x00`을 전부 `0x00 0xFF`로 escape 처리해두고 끝에 진짜 `0x00` 하나를 터미네이터로 붙인다.
그러면 여러 컬럼을 이어붙인 복합 키에서도, 각 필드가 어디서 끝나는지 별도 길이 정보 없이 바이트 스캔만으로 알 수 있다

- **InnoDB(헤더에 길이 목록)나 SQLite(헤더에 타입+길이)처럼 헤더를 따로 두지 않고, 값 자체에 경계 정보를 내장**하는 방식이다.

**DESC 정렬 — 인코딩된 바이트 전체를 비트 반전**

```kotlin
fun ByteArray.invert(): ByteArray { ... this[idx].inv() ... }
```

컬럼이 내림차순이면 정렬 가능 인코딩 결과 바이트 전체를 그냥 비트 반전시킨다

**VarInt(LEB128)는 키 인코딩이 아니라 별도 용도**

```kotlin
// Encoder.kt
fun encodeVarInt(value: Int): ByteArray { ... }  // 7비트씩 잘라 작은 수는 작은 공간에
```

[SlottedPage.kt](../../src/main/kotlin/storageEngine/page/SlottedPage.kt)에서 레코드/값 길이를 압축 저장하는 데 쓰인다. **정렬 가능 인코딩과는 목적이 다르다** - LEB128은 값이 클수록 바이트 수가 늘어나서, 바이트를 그대로 비교하면 오히려 순서가 깨진다(`300`이 `1`보다 바이트 길이가 길어서 memcmp 결과가 부정확). 그래서 키 비교용(`BaseKeySerializer`)이 아니라 순수 공간 절약용으로만 쓰인다.

### 4-way 비교

|                     | InnoDB                    | PostgreSQL                | SQLite                               | jsDB                             |
| ------------------- | ------------------------- | ------------------------- | ------------------------------------ | -------------------------------- |
| 비교 방식           | memcmp                    | 타입별 비교 함수(opclass) | 타입별 비교(역직렬화 후)             | memcmp                           |
| 정수 인코딩         | 빅엔디안 + 부호 비트 반전 | 네이티브 표현 그대로      | 값 크기에 맞는 가변 폭, 그대로       | 빅엔디안 + `xor MIN_VALUE`       |
| 가변 길이 경계 표시 | 레코드 헤더의 길이 목록   | 값 앞 길이 헤더(varlena)  | 레코드 헤더의 serial type(타입+길이) | 값 안 `0x00` 터미네이터 + escape |
| NULL 표시           | 헤더의 null bitmap        | NULL 비트맵(heap)/부재    | serial type 0                        | 값 앞 flag byte(`0x00`)          |
| DESC 정렬           | 물리적으로 반대 방향 저장 | 비교 함수가 방향 처리     | 비교 함수가 방향 처리                | 인코딩 바이트 전체 비트 반전     |

### 장단점

- **memcmp 진영(InnoDB/jsDB)**: 비교가 순수 바이트 연산이라 빠르고 구현도 (일단 인코딩만 맞으면) 단순하지만, 타입마다 "바이트 순서 = 값 순서"가 되는 인코딩 규칙을 전부 직접 설계/구현해야 한다 — 부호 반전, escape, 비트 반전 등 트릭이 타입 개수만큼 늘어난다.
- **타입별 비교 진영(PostgreSQL/SQLite)**: 저장 형식이 자연스러워 인코딩 자체는 단순하지만, 비교 경로에 함수 호출/역직렬화 비용이 항상 낀다. 대신 새로운 타입을 추가하기는 오히려 쉽다(비교 함수 하나만 정의하면 되고, byte-comparable 인코딩을 새로 고안할 필요가 없음).

### 이 프로젝트에서는

memcmp 방식을 택했다.
비교를 단순화하면서, InnoDB의 방식보다는 조금 더 구현이 직관적인 방식을 선택했다.(PostgreSQL의 경우 타입별 비교를 하지만, 약간의 최적화가 되어 있다.)
InnoDB와 같은 부호 비트 반전 트릭을 쓰되, 가변 길이 필드 경계는 헤더 대신 SQLite식에 더 가까운 "값 안에 경계 표시"로 단순화했다.
