# BUG-024 Key unpacking — 컬럼 타입별 offset 계산 오류로 다음 컬럼 데이터 오염

- **커밋:** `417a62a`
- **날짜:** 2025-06-08
- **컴포넌트:** `KeyTool.kt` — `unpackKey`
- **상태:** 수정 완료

## 증상

복합 key를 unpack할 때 두 번째 컬럼 이후의 값이 잘못 복원됨. STRING/BYTES/VarInt 타입이 포함된 스키마에서 언제나 재현.

## 원인

```kotlin
// 수정 전
for (column in schema.columns){
    if (offset >= bytes.size) break

    if(bytes[offset++] == 0x00.toByte()) {
        unpackedKeys.add(null)
        continue
    }

    val raw = mutableListOf<Byte>()
    while (offset < bytes.size && unpackedKeys.size < schema.columns.size){
        raw.add(bytes[offset++])
        if(
            column.type != ColumnType.BYTES &&
            column.type != ColumnType.STRING &&
            column.type != ColumnType.INT &&
            column.type != ColumnType.LOCAL_DATE
        ) break  // fixed-width types
    }

    val restored = raw.toByteArray().invert(column.descending)

    val value = when (column.type){
        ColumnType.INT -> decodeVarInt(restored).first
        ColumnType.STRING -> {
            val (len, lenBytes) = decodeVarInt(restored)
            val strBytes = restored.copyOfRange(lenBytes, lenBytes + len)
            strBytes.toString(StandardCharsets.UTF_8)
        }
        ...
    }
}
```

inner while의 종료 조건이 두 가지 경우를 잘못 처리:

**케이스 A — fixed-width 타입 (BOOLEAN, BYTE, SHORT, LONG, FLOAT, DOUBLE 등)**
`break` 조건(`!= BYTES && != STRING && != INT && != LOCAL_DATE`)에 해당하므로 1바이트만 읽음.
그러나 SHORT(2바이트), LONG(8바이트) 등은 1바이트만 읽어 복원 값이 완전히 잘못됨.

**케이스 B — variable-width 타입 (STRING, BYTES, INT, LOCAL_DATE)**
`break` 조건에 해당하지 않아 루프가 계속 돌면서 `bytes.size`까지 **남은 전체 바이트**를 `raw`에 넣음.
`restored`에 현재 컬럼 이후의 모든 컬럼 데이터까지 포함됨.

```
packed bytes: [null=0x01][INT varint...][STRING len varint][STRING bytes...][next col...]
                                          ↑ offset
raw = INT varint + STRING len + STRING bytes + next col + ...  ← 전부 흡수
```

이후 `decodeVarInt(restored)` 또는 `copyOfRange`가 엉뚱한 범위를 읽어 값 오염.
첫 번째 컬럼이 fixed-width라도 두 번째 컬럼 진입 시 `offset`이 bytes.size에 도달해 `break`되거나 다음 컬럼 데이터를 잘못 읽음.

## 수정

`unpackKeyItem(bytes, offset, column): Pair<Any?, Int>`를 별도 함수로 추출. 컬럼 타입별로 정확한 바이트 수만 소비하고, 소비한 바이트 수를 반환.

```kotlin
fun unpackKeyItem(bytes: ByteArray, offset: Int, column: Column): Pair<Any?, Int> {
    var position = offset
    val nullFlag = bytes[position++]
    if (nullFlag.toInt() == 0x00) return null to 1

    fun readBytes(length: Int): ByteArray {
        val slice = bytes.copyOfRange(position, position + length)
        position += length
        return slice.invert(descending)
    }

    val result: Any? = when (columnType) {
        ColumnType.BOOLEAN -> bytes[position++].toInt() != 0          // 1바이트
        ColumnType.SHORT   -> ByteBuffer.wrap(readBytes(2)).short      // 정확히 2바이트
        ColumnType.LONG    -> ByteBuffer.wrap(readBytes(8)).long       // 정확히 8바이트
        ColumnType.INT     -> {
            val (value, size) = decodeVarInt(bytes, position)
            position += size   // varint가 실제 소비한 바이트 수만큼 이동
            value
        }
        ColumnType.STRING  -> {
            val (len, lenBytes) = decodeVarInt(bytes, position)
            position += lenBytes
            readBytes(len)     // 길이 헤더 + 실제 문자열 바이트만 읽음
                .toString(StandardCharsets.UTF_8)
        }
        // UUID: 정확히 16바이트, BYTES: varint 길이 + 실제 바이트 등
        ...
    }
    return result to (position - offset)   // 소비한 총 바이트 수 반환
}
```

상위 루프:

```kotlin
for (column in schema.columns){
    val (value, consumed) = unpackKeyItem(bytes, offset, column)
    unpackedKeys.add(value)
    offset += consumed   // 실제 소비량만큼만 이동
}
```

`invert(descending)`을 각 readBytes 호출 시점에 수행하므로 다음 컬럼 데이터에 영향 없음.
