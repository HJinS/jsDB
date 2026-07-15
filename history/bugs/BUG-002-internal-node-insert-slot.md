# BUG-002 Internal Node Insert 위치 오류

- **커밋:** `76e17a2`
- **날짜:** 2025-07-04
- **컴포넌트:** `BTree.kt`, `InternalNode.kt`
- **상태:** 수정 완료

## 증상

Internal node에 promotion key를 삽입할 때 잘못된 슬롯에 삽입되어 트리 구조가 틀어짐. 이후 search가 잘못된 child page를 따라가게 됨.

## 원인

`insertData` 호출 시 슬롯 인덱스 계산 오류. `search()` 결과를 그대로 사용해야 하는데 +1/-1 offset이 잘못 적용됨.

## 수정

Internal node에 key 삽입 시 슬롯 인덱스 계산 로직 수정. 관련 테스트 케이스 추가.
