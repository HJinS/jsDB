# BUG-007 Key 삭제 후 InternalNode Separator Key 미갱신

- **커밋:** `c6ebe0b`
- **날짜:** 2025-07-27
- **컴포넌트:** `BTree.kt`, `InternalNode.kt`, `Node.kt`
- **상태:** 수정 완료

## 증상

LeafNode에서 키 삭제 후 underflow 발생 시, InternalNode의 separator key가 갱신되지 않아 이후 검색이 삭제된 키를 가리키거나 잘못된 subtree로 이동.

## 원인

`handleUnderflow`에서 redistribute 완료 후 부모 separator key를 업데이트하는 경로가 일부 케이스에서 누락됨.

## 수정

redistribute 완료 후 부모 separator key를 올바르게 갱신하는 로직 보완.
