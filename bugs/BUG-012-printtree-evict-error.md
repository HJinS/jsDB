# BUG-012 printTree 호출 시 BufferPool Evict 불가 에러

- **커밋:** `c196b57`
- **날짜:** 2026-06-24
- **컴포넌트:** `BTree.kt` — `printTree`
- **상태:** 수정 완료

## 증상

`printTree()` 호출 시 "evict할 page가 없음" 오류 발생.

## 원인

`printTree` 내부에서 BFS로 페이지를 탐색할 때 처리한 페이지의 lock을 즉시 해제하지 않음. 모든 프레임이 pin된 상태에서 새 페이지를 fetch하려 하면 evict 대상이 없어 예외 발생.

## 수정

각 노드 처리 후 해당 페이지의 lock을 즉시 `closeAndRemoveLock`으로 해제하도록 수정. 한 번에 pin되는 프레임 수를 최소로 유지.
