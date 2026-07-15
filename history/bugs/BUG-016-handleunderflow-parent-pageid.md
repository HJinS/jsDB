# BUG-016 handleUnderflow — parentPage 생성 시 잘못된 pageId

- **커밋:** `119e3b7d`
- **날짜:** 2026-07-09 (분석) / 2026-07-15 (커밋)
- **컴포넌트:** `BTree.kt` — `handleUnderflow`
- **상태:** 수정 완료

## 증상

`handleUnderflow`에서 parent 노드의 pageId getter 및 예외 메시지가 잘못된 값을 반환. 기능적 영향은 미미하나 디버깅 혼란 유발.

## 원인

```kotlin
// 수정 전 (버그)
parentLock.asWriteView { parentBuffer ->
    val parentPage = SlottedPage(indexConfig, currentPageId, parentBuffer)
    //                                        ^^^^^^^^^^^^ 자식 노드의 pageId
```

`parentBuffer`는 부모 노드의 실제 데이터인데 `currentPageId`(자식)를 pageId로 전달.
데이터 읽기/쓰기는 ByteBuffer 기반이라 기능적으로는 문제없으나 `page.pageId` getter와 예외 메시지가 틀린 값을 보여줌.

## 수정

```kotlin
// 수정 후
val parentPage = SlottedPage(indexConfig, nextTrace.first, parentBuffer)
//                                        ^^^^^^^^^^^^^^^^ 부모 노드의 pageId
```
