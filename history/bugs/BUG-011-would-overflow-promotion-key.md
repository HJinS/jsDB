# BUG-011 wouldOverflow 조건 부정확 + Split 후 promotionKey 선택 버그

- **커밋:** `0ae6f86`
- **날짜:** 2026-06-23
- **컴포넌트:** `BTree.kt`, `Node.kt`
- **상태:** 수정 완료

## 증상

1. overflow 판단이 부정확하여 불필요한 split 발생 또는 overflow 미감지.
2. split 후 새 키를 좌/우 노드 중 잘못된 노드에 삽입.

## 원인

**문제 1 — wouldOverflow:**
freeSpace만 체크하고 `keyCount >= maxKeys` 조건이 누락됨. freeSpace가 충분하더라도 key 개수가 maxKeys에 도달하면 overflow여야 함.

**문제 2 — promotionKey 기준:**
split 후 새 키를 좌/우 노드 중 어디에 삽입할지 결정할 때 `separatorKey`와 비교해야 하는데, 실제로는 `separatorKey + 1` 슬롯의 키(우측 노드 첫 번째 키)가 기준이어야 함.

```
split 결과:
  [ 0 .. promotionKeyIdx ] → 좌측 노드
  [ promotionKeyIdx+1 .. n ] → 우측 노드

새 키 >= separatorKey+1슬롯의 키  → 우측 노드에 삽입
새 키 <  separatorKey+1슬롯의 키  → 좌측 노드에 삽입
```

## 수정

1. `Node.wouldOverflow`: `keyCount >= indexConfig.maxKeys` 조건 추가.
2. `BTree.insert` split 후 분기: `page.getData(promotionKeyIdx + 1).first`를 기준으로 삽입 노드 결정.
