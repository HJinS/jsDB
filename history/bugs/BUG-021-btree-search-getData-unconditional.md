# BUG-021 BTree.search — isExist=false 시 getData(keyIdx) 무조건 호출

- **커밋:** 119e3b7d
- **날짜:** 2026-07-15
- **컴포넌트:** `BTree.kt` — `search`
- **상태:** 수정 완료

## 증상

delete 이후 `btree.search(deletedKey) shouldBe null` 테스트에서 null 반환 대신 다음 예외 발생:

```
storageEngine.exception.SlottedPageException$SlotOutOfBoundException at BTreeTest.kt:681
```

BUG-019 수정(getData bounds check 추가) 이후 표면화됨. 이전에는 묵살되던 잠재적 버그.

## 원인

`BTree.search`에서 키가 존재하지 않는 경우(`isExist=false`)에도 `getData(keyIdx)`를 무조건 호출했다.

```kotlin
// 수정 전
val (leafNodePageId, keyIdx, isExist) = searchLeafNode(...)
val value: ByteArray = lock.asReadView { buffer ->
    val currentPage = SlottedPage(...)
    val node = Node.from(...)
    if(node.isSafeNode(BTreeOptMode.SELECT)) lockManager.realeaseAncester(lock)
    currentPage.getData(keyIdx).second  // ← isExist=false여도 무조건 호출
}
return if(isExist) valueSerializer.deserialize(value) else null
```

### keyIdx가 recordCount가 되는 경우

`searchLeafNode`가 반환하는 `keyIdx`는 `LeafNode.search(key, exactIndex=true)`의 결과다:

```kotlin
fun search(key: ByteArray, exactIndex: Boolean=false): Pair<Int, Boolean>{
    val idx = page.binarySearch(key)
    return if(idx >= 0) idx to true else -(idx + 1) to false
}
```

키가 없으면 `binarySearch`가 `-(insertionPoint + 1)`을 반환하고, `search`는 `insertionPoint to false`를 반환한다. **삭제된 키가 리프의 모든 키보다 크면 `insertionPoint == recordCount`**, 즉 `keyIdx == recordCount`가 된다.

### BUG-019 수정 전후의 동작 차이

| 상황 | BUG-019 수정 전 | BUG-019 수정 후 |
|------|-----------------|-----------------|
| `keyIdx == recordCount` | 잔존 바이트에서 garbage 값 반환 (length≠0이면 정상처럼 동작) | `SlotOutOfBoundException` 발생 |
| `keyIdx < recordCount` | 다른 키의 value를 반환 (isExist=false이므로 어차피 버려짐) | 동일 |

BUG-019 수정 전에는 잔존 바이트 덕분에 예외가 발생하지 않거나, length=0 체크에만 걸려 `SlotOutOfBoundException`이 가끔 발생했다. 수정 후에는 `keyIdx >= recordCount`마다 항상 예외 발생.

## 수정

`isExist=false`이면 `getData`를 호출하지 않도록 조건 추가:

```kotlin
// 수정 후
val value: ByteArray? = lock.asReadView { buffer ->
    val currentPage = SlottedPage(indexConfig, leafNodePageId, buffer)
    val node = Node.from(indexConfig, currentPage, keySerializer)
    if(node.isSafeNode(BTreeOptMode.SELECT)) lockManager.realeaseAncester(lock)
    if (isExist) currentPage.getData(keyIdx).second else null
}
lockManager.close()
traceNode.clear()
return value?.let { valueSerializer.deserialize(it) }
```

## 관련

- BUG-019: `getData` bounds check 누락 — 본 버그를 표면화시킨 수정
