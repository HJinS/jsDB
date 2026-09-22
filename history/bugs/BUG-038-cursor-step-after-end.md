# BUG-038 Cursor.step — 트리 끝에 도달한 뒤 다시 호출하면 NoSuchElementException

- **커밋:** `f4153cf` (수정) / `c943e0e` (Cursor 도입, 2026-09-16)
- **날짜:** 2026-09-19
- **컴포넌트:** `Cursor.kt` — `step`
- **상태:** 수정 완료
- **이슈:** #41

## 증상

스캔이 트리 끝까지 간 뒤에 `step()`을 한 번 더 호출하면 `NoSuchElementException`이 났다.
BTree 스캔 테스트에서 "끝까지 순회한 뒤 null이 반환되는지" 검증하는 케이스에서 발견됐다.

## 원인

`reachedEnd`가 되는 분기(마지막 slot이면서 이웃 페이지도 없는 경우)에서는 마지막 값을 정상 반환하고 lock을 `closeAndRemoveLock`으로 지웠지만, `currentPosition`은 갱신하지 않았다.
다음 `step()` 호출은 `currentPosition`이 아직 유효하다고 보고 `lockManager.last`를 다시 읽는데, 큐는 이미 비어 있었다.

## 수정

- 끝에 도달하면 `currentPosition = SearchPosition(INVALID_PAGE_ID, null)`로 명시적으로 표시.
- `step()` 맨 앞에 `if (currentPosition.pageId == INVALID_PAGE_ID) return null` 가드를 추가.

```kotlin
fun step(): Pair<ByteArray, ByteArray>? {
    while (true) {
        val currentPageId = currentPosition.pageId
        if (currentPosition.pageId == INVALID_PAGE_ID) return null   // 추가
        ...
        if (neighborPageId == INVALID_PAGE_ID) {
            reachedEnd = true
            currentPosition = SearchPosition(neighborPageId, null)   // 추가
        }
```

이후 `step()`의 `null`은 "트리가 물리적으로 끝남"을 뜻한다.

## 관련

- BUG-039: 같은 커밋의 `findExtremeLeafPageId` 중복 lock
- `docs/index/range-scan-design.md`의 "`Cursor.step()`/`findExtremeLeafPageId` 버그 두 건"
