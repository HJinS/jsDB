# BUG-010 ByteBuffer Overlap으로 인한 데이터 손상

- **커밋:** `d8a2c48`
- **날짜:** 2026-06-24
- **컴포넌트:** `SlottedPage.kt`, `Encoder.kt`
- **상태:** 수정 완료

## 증상

슬롯 데이터를 이동할 때 src와 dst 영역이 겹치는 경우 데이터가 덮어씌워져 손상됨.

## 원인

`ByteBuffer`를 이용한 byte 이동 시 overlap 조건을 고려하지 않음.

overlap 조건:
- `src < dst`
- `src + length > dst`

이 경우 앞에서부터 복사하면 아직 복사하지 않은 src 영역을 dst가 덮어씀.

## 수정

overlap 조건(`src < dst && src + length > dst`) 감지 후 뒤에서부터(reverse order) 복사하도록 수정.
