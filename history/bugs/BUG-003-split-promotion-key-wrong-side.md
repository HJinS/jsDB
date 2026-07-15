# BUG-003 Split 시 Promotion Key가 좌측 노드에 잔류

- **커밋:** `1f5f6c2`
- **날짜:** 2025-07-11
- **컴포넌트:** `BTree.kt`, `InternalNode.kt`, `LeafNode.kt`
- **상태:** 수정 완료

## 증상

노드 split 후 promotion key가 새로 생성된 우측 노드가 아닌 좌측 노드에 잔류. 부모 노드가 올바른 separator key를 받지 못해 검색 경로 오류 발생.

## 원인

`split()` 함수에서 promotion key index 기준이 잘못 설정됨. LeafNode의 경우 promotion key는 우측 노드의 첫 번째 키여야 하는데, 좌측에 포함된 채로 split data가 계산됨.

## 수정

- `LeafNode.split()`: promotion key가 우측 노드에 남도록 분리 기준 수정.
- `InternalNode.split()`: promotion key는 부모로 올라가고 좌우 노드에서 제거되도록 수정.
