# BUG-005 BTree Delete — Left Sibling 판별 기준 오류

- **커밋:** `b20bf7b`
- **날짜:** 2025-07-25
- **컴포넌트:** `BTree.kt`, `InternalNode.kt`, `LeafNode.kt`, `Node.kt`
- **상태:** 수정 완료

## 증상

`redistribute` 및 `merge` 시 현재 노드가 left인지 right인지 판별하는 기준이 잘못되어 sibling에서 키를 빌리거나 병합할 때 방향이 반전됨. 잘못된 separator key 갱신 또는 잘못된 노드 삭제 발생.

## 원인

`isLeft` 판별 시 현재 노드 자신의 pageId를 기준으로 비교하던 것을 부모 노드의 child pointer 기준으로 비교하도록 변경 필요. 기존 로직은 부모 컨텍스트 없이 노드 자체 정보만으로 판별하려다 오류 발생.

## 수정

`Node.isLeft()`: 부모 노드의 `childPageId(keyIdx+1)` 값과 target 노드의 pageId를 비교하는 방식으로 변경.
