# BUG-009 SlottedPage shift/binarySearch 버그

- **커밋:** `f1426a0`
- **날짜:** 2026-05-04
- **컴포넌트:** `SlottedPage.kt`
- **상태:** 수정 완료

## 증상

레코드 삽입/삭제 시 슬롯 이동(shift) 로직이 잘못되어 데이터가 덮어써지거나 손상됨. `binarySearch`가 잘못된 인덱스를 반환해 삽입 위치가 틀어짐.

## 원인

- `shift`: 슬롯 디렉토리를 이동할 때 이동 범위 계산 오류.
- `binarySearch`: comparator가 키 비교 방향을 반대로 적용.

## 수정

`SlottedPage.kt` 전반적인 shift, binarySearch 로직 재작성.
