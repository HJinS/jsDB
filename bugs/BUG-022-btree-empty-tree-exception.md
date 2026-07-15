# BUG-022 빈 트리에서 search/traverse — null/emptyList 대신 예외 발생

- **커밋:** 미커밋 (현재 세션 수정)
- **날짜:** 2026-07-15
- **컴포넌트:** `BTree.kt` — `search`, `traverse`, `traverseWithPages`
- **상태:** 수정 완료

## 증상

전체 데이터를 삭제해 트리가 완전히 비워진 후(`rootPageId == -1L`):

```
// loop 2999 (마지막 삭제 후)
btree.traverseWithPages()
→ index.exception.BTreeException$LeafNodeNotFoundException

btree.search(lastDeletedKey)
→ index.exception.IndexException$EmptyTreeException
```

## 원인

### search

`searchLeafNode` 진입 시 빈 트리를 즉시 예외로 처리했다:

```kotlin
private fun searchLeafNode(...): Triple<Long, Int, Boolean> {
    if(rootPageId == -1L) throw IndexException.EmptyTreeException(name, targetTable)
    // ...
}
```

`search`는 이 예외를 잡지 않아 호출자에게 전파됨. 빈 트리에서 search는 의미상 `null` 반환이 맞다.

### traverse / traverseWithPages

`findLeftMostLeafPageId`는 빈 트리(`rootPageId == -1L`)에서 `null`을 반환하도록 올바르게 구현되어 있었다. 그러나 `traverse`와 `traverseWithPages`에서 이 `null`을 예외로 처리했다:

```kotlin
// 수정 전
var leafNodePageIdCursor: Long? =
    findLeftMostLeafPageId(lockManager) ?: throw BTreeException.LeafNodeNotFoundException(null)
```

빈 트리에서 traverse는 의미상 빈 리스트 반환이 맞다.

## 수정

### search — 빈 트리 조기 반환

```kotlin
fun search(key: K): V?{
    if (rootPageId == -1L) return null
    // ...
}
```

### traverse / traverseWithPages — null 시 빈 리스트 반환

```kotlin
// traverse
var leafNodePageIdCursor: Long? =
    findLeftMostLeafPageId(lockManager) ?: return emptyList()

// traverseWithPages
var leafNodePageIdCursor: Long? =
    findLeftMostLeafPageId(lockManager) ?: return emptyList()
```

## 발견 경위

3000개 키를 모두 삭제하는 테스트(loop 0~2999)에서 마지막 루프(2999) 직후 발견. 마지막 삭제 시 single-leaf root가 완전히 비워지면 `handleUnderflow`의 isRoot 블록이 `rootPageId = -1L`로 설정하고 root 페이지를 삭제함.
