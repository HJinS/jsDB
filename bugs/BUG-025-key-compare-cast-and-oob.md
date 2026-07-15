# BUG-025 Key compare — Comparable 타입 캐스팅 오류 & out-of-bounds

- **커밋:** `dca56eb`
- **날짜:** 2025-06-13
- **컴포넌트:** `Comparer.kt` — `compareUnpackedKey`, `comparePackedKey` / `Encoder.kt` — `decodeVarInt`
- **상태:** 수정 완료

## 증상

- 복합 key 비교 시 `ClassCastException` 발생 (INT, LONG 컬럼 포함 스키마).
- 두 key의 바이트 배열 길이가 다를 때 `ArrayIndexOutOfBoundsException`.
- descending VarInt 컬럼이 있는 스키마에서 `decodeVarInt`가 1바이트만 읽고 종료되어 잘못된 값 반환.

## 원인

### 1. compareUnpackedKey — Comparable<Any> 캐스팅 오류

```kotlin
// 수정 전
else -> (value1 as Comparable<Any>).compareTo(value2 as Comparable<Any>)
```

Kotlin의 숫자 타입은 `Comparable<Int>`, `Comparable<Long>` 등이지 `Comparable<Any>`가 아님.
`Int`를 `Comparable<Any>`로 캐스트하면 런타임에 `ClassCastException` 발생.
(특히 JVM에서 `Comparable<Int>.compareTo(Any)`는 타입 파라미터 불일치로 실패.)

### 2. compareUnpackedKey — otherKey index out of bounds

```kotlin
// 수정 전
val value2 = otherKey[idx]
```

두 key의 컬럼 수가 다를 경우(partial key 비교 등) `IndexOutOfBoundsException`.

### 3. comparePackedKey — other ByteArray out of bounds

```kotlin
// 수정 전
val byte2 = other[offset2]
```

두 packed key의 바이트 길이가 다를 때 `ArrayIndexOutOfBoundsException`.
null flag 비교 단계에서 `other`가 이미 끝난 경우 즉시 크래시.

### 4. decodeVarInt — descending 처리 시 MSB 체크 오류로 조기 종료

```kotlin
// 수정 전
while (true) {
    var byte = bytes[pos]
    if (descending) {
        byte = (byte.toInt() xor 0xFF).toByte()  // 비트 반전
    }
    result = result or ((byte.toInt() and 0x7F) shl shift)
    shift += 7
    pos++
    if ((byte.toInt() and 0x80) == 0) break  // ← 반전된 byte로 체크
}
```

descending VarInt 인코딩은 각 바이트를 `xor 0xFF`로 저장. 즉 연속 바이트의 MSB(continuation bit)도 반전됨.

예: 원본 2바이트 VarInt `[0x81, 0x01]` (값 129) → descending 저장 `[0x7E, 0xFE]`

decode 시 `byte = 0x7E`, 반전 후 `0x81` → `0x80 & 0x81 != 0` → continue (정상).
그러나 `byte = 0xFE`, 반전 후 `0x01` → `0x80 & 0x01 == 0` → break (정상).

실제로는 이 방향이 맞는 것처럼 보이지만, packed key 비교에서는 이미 저장된 바이트를 그대로 byte-level 비교하므로 `decodeVarInt`로 값을 복원할 필요 자체가 없음. 불필요한 descending decode 로직이 혼재하여 호출 측에서 어느 경로를 타는지에 따라 잘못된 값이 반환됨.

## 수정

### compareUnpackedKey — 타입별 명시적 캐스트

```kotlin
// 수정 후
val value2 = otherKey.getOrNull(idx)   // 안전한 접근

else -> when (column.type) {
    ColumnType.INT          -> (value1 as Int).compareTo(value2 as Int)
    ColumnType.LONG         -> (value1 as Long).compareTo(value2 as Long)
    ColumnType.LOCAL_DATE   -> (value1 as LocalDate).compareTo(value2 as LocalDate)
    ColumnType.UUID         -> (value1 as UUID).compareTo(value2 as UUID)
    ColumnType.FLOAT        -> (value1 as Float).compareTo(value2 as Float)
    ColumnType.DOUBLE       -> (value1 as Double).compareTo(value2 as Double)
    ColumnType.BOOLEAN      -> (value1 as Boolean).compareTo(value2 as Boolean)
    ColumnType.SHORT        -> (value1 as Short).compareTo(value2 as Short)
    ColumnType.INSTANT      -> (value1 as Instant).compareTo(value2 as Instant)
    ColumnType.LOCAL_DATE_TIME -> (value1 as LocalDateTime).compareTo(value2 as LocalDateTime)
}
```

### comparePackedKey — 안전한 배열 접근

```kotlin
// 수정 후
val byte2 = other.getOrElse(offset2) { 0x00.toByte() }  // 범위 초과 시 null(0x00) 취급
```

### comparePackedItem(INT) — 값 복원 대신 byte-level 비교

```kotlin
// 수정 전
ColumnType.INT -> {
    val (value1, length1) = decodeVarInt(bytes1, offset1, column.descending)
    val (value2, length2) = decodeVarInt(bytes2, offset2, column.descending)
    Triple(value1.compareTo(value2), length1, length2)
}

// 수정 후
ColumnType.INT -> {
    val (_, length1) = decodeVarInt(bytes1, offset1, column.descending)
    val (_, length2) = decodeVarInt(bytes2, offset2, column.descending)
    Triple(bytes1.compareTo(bytes2), length1, length2)  // raw byte 비교
}
```

packed key는 정렬 순서를 보존하도록 인코딩되어 있으므로 byte-level 비교가 맞음.
descending 컬럼은 인코딩 시점에 이미 비트 반전되어 있어 역순 정렬이 자동 적용됨.

### decodeVarInt — descending 분기 제거 및 while 조건 수정

```kotlin
// 수정 후
while (pos < bytes.size) {
    var byte = bytes[pos].toInt() and 0xFF  // unsigned 처리, descending XOR 제거
    result = result or ((byte and 0x7F) shl shift)
    pos++
    if ((byte and 0x80) == 0) break
    shift += 7
}
return result to (pos - offset)
```

`while(true)` → `while(pos < bytes.size)`로 배열 오버런 방지.
descending 처리는 인코딩/디코딩 호출 측에서 담당하도록 책임 분리.
