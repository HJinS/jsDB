# BUG-032 LeafNode.redistribute — 오른쪽 sibling에서 빌릴 때 separator를 잘못된 키로 갱신

- **커밋:** `719d3e6`
- **날짜:** 2026-05-04
- **컴포넌트:** `LeafNode.kt` — `redistribute`
- **상태:** 수정 완료

## 증상

leaf가 오른쪽 sibling에서 키를 하나 빌린 뒤, 빌려 온 키를 search해도 찾지 못했다.

## 원인

오른쪽 sibling에서 가장 작은 키를 가져오면(`borrow from right`), 부모의 separator는 "오른쪽 sibling의 **새 첫 번째 키**"가 되어야 한다.
그런데 코드는 옮겨 온 키 자체를 separator로 썼다.

```kotlin
// 수정 전
if(isLeft(targetNode.page.pageId, parentNode, keyIdx)){      // 오른쪽 sibling에서 빌림
    val (key, value) = targetNode.deleteData(0)
    insert(key, value)
    parentNode.updateKey(keyIdx, key)                         // 빌려 온 키 → 이미 왼쪽 노드에 있음
}
```

separator는 "그 키 이상은 오른쪽 노드"라는 뜻이므로, 왼쪽으로 옮겨진 키가 separator가 되면 그 키를 찾는 탐색이 오른쪽으로 내려가 miss가 난다.
코드 위 주석에는 "Update the parent node's separator keys to the new smallest key of the right sibling"이라고 올바르게 적혀 있어서, 코드가 자신의 문서와 달랐다.

## 수정

```kotlin
// 수정 후
val (key, value) = targetNode.deleteData(0)
insert(key, value)
parentNode.updateKey(keyIdx, targetNode.page.getData(0).first)   // 오른쪽 sibling의 새 첫 키
```

왼쪽 sibling에서 빌리는 분기는 빌려 온 키가 곧 이 노드의 새 첫 키이므로 기존대로 `key`를 쓴다.

## 관련

- BUG-006: 왼쪽 sibling에서 빌릴 때 separator 인덱스(`keyIdx-1`) off-by-one
- BUG-007: redistribute 이후 부모 separator 갱신 누락
- BUG-031: 같은 커밋의 `Node.deleteData` 수정
