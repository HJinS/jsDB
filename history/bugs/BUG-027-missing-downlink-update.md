# BUG-027 Missing BTree downlink update - 삭제 시에 부모 downlink 업데이트 누락

- **커밋:** `b4824a8`
- **날짜:** 2026-08-12
- **컴포넌트:** `BTree.kt` — , `delete`, `search`, `searchLeafNode`, `split`, `handleUnderflow`
- **상태:** 수정 완료

## 증상
issue #33
BTreeTest를 돌릴때 간헐적으로 테스트가 실패함. 예상 key를 정상적으로 찾지 못하는 현상


## 원인
여러가지 원인이 복합적으로 있음.
1. 삭제 시에 LeafPage의 0번 idx인 데이터를 삭제 할 경우 부모 downlink를 갱신할 필요가 있다.
   1. 부모의 idx가 0인경우 trace를 따라 0이 아닌 부모까지 올라가서 갱신이 필요하다.
2. 리벨런싱이나 삭제 시에 writeLock을 잡고있는지 확인을 하고 있지 않음.
   1. 확인 후에 writeLock이 없는 경우 다시 writeLock을 잡을 필요가 있다.
3. 테스트 코드에서 key 확인 시에 dummy-value를 기준으로 검증을 진행하고 있었다.
   1. 이 부분은 더미 key를 따로 관리하여 검증에 사용할 필요가 있음.


## 수정
- [propagateSeparatorUpdate](../../src/main/kotlin/index/btree/BTree.kt#L199) 추가 및 삭제 시에 keyIdx == 0 인 경우 호출
- 아래와 같이 lock 확인 후 필요시 refetch

```kotlin
val parentLock = if (nextTrace.third.isWriteLocked) {
    nextTrace.third
} else {
    val refetchedLock = storageManager.fetchPage(nextTrace.first, LockMode.WRITE)
    lockManager.push(refetchedLock)
    refetchedLock
}
```

- 테스트 코드에서 검증시에 별도의 expectedKey를 관리하여 검증 수행
