# BUG-037 packKeyItem — NULL이 desc 컬럼에서도 항상 NULLS FIRST로 인코딩됨

- **커밋:** `5c82720` ("lint 정리" 커밋에 함께 포함됨)
- **날짜:** 2026-09-16
- **컴포넌트:** `BaseKeySerializer.kt` — `packKeyItem`
- **상태:** 수정 완료
- **이슈:** #41 (range scan 작업 중 함께 수정)

## 증상

DESC 컬럼의 NULL이 ASC와 똑같이 가장 앞에 정렬됐다.
또 `MultiColumnKeySerializer`가 뒤쪽 컬럼을 생략(prefix)할 때 쓰는 패딩은 DESC에 `0xFF`를 사용하고 있어서, 같은 인덱스 안에서 NULL 표현과 패딩이 서로 어긋났다.

## 원인

`packKeyItem`이 NULL이면 DESC 비트 반전 단계에 도달하기 전에 바로 `0x00`을 반환했다.

```kotlin
// 수정 전
if(key == null) return byteArrayOf(0x00)     // desc 반전을 건너뜀
```

DESC 컬럼은 다른 값들이 모두 비트 반전되어 저장되는데 NULL만 반전되지 않아, 정렬 위치가 DESC에서도 "가장 작은 값"에 남아 있었다.
즉 ASC에서는 NULLS FIRST, DESC에서도 NULLS FIRST가 되었다.

## 수정

```kotlin
// 수정 후
if (key == null) {
    return if (indexColumn.descending) byteArrayOf(0xFF.toByte()) else byteArrayOf(0x00)
}
```

NULL을 "최솟값"으로 취급하는 MySQL/SQLite 관례를 따른다

- ASC는 NULLS FIRST(`0x00`), DESC는 NULLS LAST(`0xFF`).
  `MultiColumnKeySerializer`의 prefix 패딩과도 일관된다.

| DB             | NULL 취급   | ASC   | DESC  |
| -------------- | ----------- | ----- | ----- |
| PostgreSQL     | 최댓값      | LAST  | FIRST |
| MySQL / SQLite | 최솟값      | FIRST | LAST  |
| jsDB (수정 전) | 항상 `0x00` | FIRST | FIRST |
| jsDB (수정 후) | 최솟값      | FIRST | LAST  |

## 참고

- 이 수정은 별도 커밋이 아니라 `lint 정리` 커밋(`5c82720`)에 포함되어 있다.
- 설계 배경은 `docs/index/range-scan-design.md`의 "NULL 정렬 위치" 참고.

## 관련

- BUG-004: key 비교 순서 오류 (packing 시 invert)
- BUG-038, BUG-039: 같은 이슈(#41) 작업 중 발견된 Cursor/BTree 버그
