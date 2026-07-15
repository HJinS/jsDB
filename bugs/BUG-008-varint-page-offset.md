# BUG-008 Varint Encoding 오류 및 Page Offset 버그

- **커밋:** `21c05f3`
- **날짜:** 2025-10-07
- **컴포넌트:** `Encoder.kt`, `Page.kt`
- **상태:** 수정 완료

## 증상

varint encoding/decoding 결과가 일부 경계값에서 틀림. Page의 슬롯 offset 계산이 잘못되어 데이터 읽기/쓰기 위치가 어긋남.

## 원인

- **varint:** LEB128 encode 시 마지막 바이트에 continuation bit 처리 누락.
- **page offset:** 헤더 크기를 고려하지 않은 raw offset 사용.

## 수정

- varint 인코딩 로직 수정.
- page offset 계산 시 헤더 offset을 포함하도록 수정.
