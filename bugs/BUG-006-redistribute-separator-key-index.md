# BUG-006 LeafNode Redistribute — Separator Key 갱신 인덱스 오류

- **커밋:** `e14cbba`
- **날짜:** 2025-07-25
- **컴포넌트:** `LeafNode.kt`
- **상태:** 수정 완료

## 증상

LeafNode에서 왼쪽 sibling으로부터 키를 빌릴 때 (borrow from left) 부모의 separator key가 잘못된 위치에 갱신됨.

## 원인

`redistribute()` 내부에서 부모 노드의 separator key를 업데이트하는 로직에서 `keyIdx-1` 대신 `keyIdx`를 사용하는 off-by-one 오류.

```
separator 구조:
  ...  K[keyIdx-1]    K[keyIdx]  ...
     P[keyIdx-1]   P[keyIdx]   P[keyIdx+1]

- 왼쪽 sibling = P[keyIdx-1]
- 현재 노드    = P[keyIdx]
- 이 둘 사이의 separator = K[keyIdx-1] (slot keyIdx-1)
```

## 수정

separator key 갱신 인덱스를 `keyIdx-1`로 수정.
