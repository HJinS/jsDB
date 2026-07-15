# BUG-026 BTree.traceNode class field — 동시성 오염으로 잘못된 부모 노드 참조

- **커밋:** `719d3e6`
- **날짜:** 2026-05-04
- **컴포넌트:** `BTree.kt` — `insert`, `delete`, `search`, `searchLeafNode`, `split`, `handleUnderflow`
- **상태:** 수정 완료

## 증상

concurrent insert/delete 상황에서 `split` 또는 `handleUnderflow` 도중 잘못된 pageId로 부모 노드를 fetch. 트리 구조 오염 또는 `InvalidTraceStackException` 발생.

단일 스레드에서도 `delete` 이후 `traceNode.clear()`가 `delete` 함수 맨 끝에서만 호출되어, 예외가 발생하거나 early return이 일어나면 스택이 남아 다음 연산을 오염시키는 문제도 존재.

## 원인

```kotlin
// 수정 전
class BTree<K, V> {
    private val traceNode: Stack<Pair<Long, Int>> = Stack()   // ← class member

    fun insert(key: K, value: V) {
        searchLeafNode(serializedKey)   // traceNode에 경로 push
        if (node.isOverflow) split()    // 같은 traceNode 소비
    }

    fun delete(key: K) {
        searchLeafNode(serializedKey)   // 같은 traceNode에 경로 push
        if (isUnderflow) handleUnderflow()
        traceNode.clear()               // ← 정상 종료 시에만 clear
    }

    private fun handleUnderflow() { traceNode.pop() ... }
    private fun split() { while(traceNode.isNotEmpty()) traceNode.pop() ... }
    private fun searchLeafNode(key: ByteArray) { traceNode.push(...) ... }
}
```

`traceNode`가 BTree 인스턴스에 하나 존재하여 모든 연산이 공유함.

**시나리오 A — 동시 insert + delete:**
1. Thread A: `insert` → `searchLeafNode` → traceNode에 `[root→node1→leaf]` push
2. Thread B: `delete` → `searchLeafNode` → 같은 traceNode에 `[root→node2→leaf2]` 추가 push
3. Thread A: `split()` → traceNode.pop()으로 B가 push한 노드를 소비 → 잘못된 페이지에 split 적용

**시나리오 B — 단일 스레드, 예외 발생 시 스택 잔류:**
1. `delete` 중 예외 발생 → `traceNode.clear()` 미실행
2. 다음 `insert` → `searchLeafNode`가 clear되지 않은 이전 스택 위에 새 경로 push
3. `split` 이전 연산의 경로까지 pop → 트리 구조 오염

## 수정

`traceNode`를 class field에서 제거하고 각 연산의 로컬 변수로 생성. `searchLeafNode`, `split`, `handleUnderflow`에 파라미터로 전달.

```kotlin
// 수정 후
class BTree<K, V> {
    // traceNode 필드 삭제

    fun insert(key: K, value: V) {
        val traceNode = Stack<Pair<Long, Int>>()   // 연산마다 새 스택 생성
        searchLeafNode(serializedKey, traceNode)
        if (node.isOverflow) split(traceNode)
    }

    fun delete(key: K) {
        val traceNode = Stack<Pair<Long, Int>>()
        searchLeafNode(serializedKey, traceNode)
        if (isUnderflow) handleUnderflow(traceNode)
        // clear() 불필요 — 로컬 변수이므로 함수 종료 시 자동 해제
    }

    fun search(key: K): V? {
        val traceNode = Stack<Pair<Long, Int>>()
        searchLeafNode(serializedKey, traceNode)
        ...
    }

    private fun searchLeafNode(key: ByteArray, traceNode: Stack<Pair<Long, Int>>): Triple<...>
    private fun split(traceNode: Stack<Pair<Long, Int>>)
    private fun handleUnderflow(traceNode: Stack<Pair<Long, Int>>)
}
```

각 연산이 독립적인 스택을 가지므로 스레드 간 오염 불가. 예외 발생 시에도 스택이 GC에 의해 회수됨.

## 추가 수정 (같은 커밋)

`searchLeafNode`의 루트 노드 early-return 로직을 단순화:

```kotlin
// 수정 전: 루트를 두 번 fetch (early-return 블록 + 메인 while 루프)
val rootNodeHandle = storageManager.fetchPage(rootPageId)
rootNodeHandle.use { ... early return if root is leaf-shaped internal ... }
// 이후 while(true) { fetchPage(pageIdCursor) ... }

// 수정 후: while 루프 안에서 루트 조건도 처리
while(true){
    storageManager.fetchPage(pageIdCursor).use { handle ->
        ...
        if(pageIdCursor == rootPageId && currentNode is InternalNode && currentNode.isLeaf){
            return Triple(pageIdCursor, searchIdx, isExist)
        }
        if(currentNode.isLeaf){ return Triple(...) }
        else { traceNode.push(nextPageId to result.first); pageIdCursor = nextPageId }
    }
}
```

루트를 별도로 fetch하던 코드가 제거되어 불필요한 페이지 핀/언핀 감소.
