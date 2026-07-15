# BUG-015 handleUnderflow — Merge 조건 논리 오류로 merge 불실행

- **커밋:** staged (미커밋)
- **날짜:** 2026-07-09 (분석) / 2026-07-12 (수정 적용)
- **컴포넌트:** `BTree.kt` — `handleUnderflow`
- **상태:** 수정 완료 (staged, 미커밋)

## 증상

3000개 삭제 테스트에서 `BufferUnderflowException` 또는 traverse 순서 오류 발생. loop 0부터 실패, diff index가 loop마다 1씩 감소.

## 원인

`handleUnderflow`에서 redistribute 실패 후 merge를 시도하는 블록에 `if(siblingNode.hasSurplusKey)` 조건이 남아있었음.

```kotlin
// 수정 전 (버그)
if(!isDone){  // 이 블록 진입 = 모든 sibling의 hasSurplusKey == false 가 보장됨
    for(siblingLock in siblingLocks){
        siblingLock.asWriteView { siblingBuffer ->
            val siblingNode = ...
            if(siblingNode.hasSurplusKey){  // ← 항상 false → merge 절대 불가
                currentNode.merge(...)
            }
        }
    }
}
```

`!isDone` 블록에 진입 = redistribute 실패 = 모든 sibling의 `hasSurplusKey == false` 가 보장됨.
그런데 merge 조건도 동일하게 `hasSurplusKey`를 체크하므로 merge가 영원히 실행되지 않음.

결과: underflow 노드 방치 → 연속 삭제 시 data area fragmentation 심화 → compaction 내부에서 `BufferUnderflowException`.

## 수정

merge 블록에서 `if(siblingNode.hasSurplusKey)` 조건 제거.

```kotlin
// 수정 후
if(!isDone){
    for(siblingLock in siblingLocks){
        if(!isMerged){
            siblingLock.asWriteView { siblingBuffer ->
                val siblingPage = SlottedPage(indexConfig, siblingLock.pageId, siblingBuffer)
                val siblingNode = Node.from(indexConfig, siblingPage, keySerializer)
                val (_, rightPageId) = currentNode.merge(siblingNode, parentNode, keyIdx)
                isMerged = true
                val victimPageLock = if(rightPageId == currentLock.pageId) currentLock else siblingLock
                lockManager.closeAndRemoveLock(victimPageLock)
                storageManager.deletePage(rightPageId)
            }
        }
    }
}
```
