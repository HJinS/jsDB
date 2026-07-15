# BUG-004 Key 비교 연산 오류 (Packing 시 invert 적용)

- **커밋:** `2883408`
- **날짜:** 2025-07-24
- **컴포넌트:** `Encoder.kt`, `Comparer.kt`, `KeyTool.kt`
- **상태:** 수정 완료

## 증상

B+Tree의 binary search가 잘못된 결과를 반환. 키 비교 순서가 뒤집혀 있어 정렬 불변식이 깨짐.

## 원인

`packing` 과정에서 일부 자료형(특히 숫자)에 invert 처리가 잘못 적용됨. memcmp 방식의 바이트 비교를 위해서는 부호 있는 정수를 unsigned 표현으로 변환해야 하는데, 이 과정에서 비트 반전이 불필요하게 적용되어 비교 결과가 역전됨.

## 수정

- packing 시 invert 로직 제거.
- 각 자료형(INT, LONG, STRING, LOCAL_DATE)의 packing/unpacking 방식 재정립.
- unpacking도 동일하게 수정.
