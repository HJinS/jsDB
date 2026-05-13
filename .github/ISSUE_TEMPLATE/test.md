---
name: "🧪 테스트"
about: "테스트 추가 또는 기존 테스트 보강"
labels: ["test"]
---

## 테스트 대상
<!-- 어떤 모듈/기능을 검증하는지 -->
- **대상 클래스**: <!-- e.g. BTree, SlottedPage, DiskManager -->
- **테스트 종류**: <!-- Unit / Property-based / Integration / Concurrency / Persistence -->

## 현재 문제
<!-- 지금 테스트가 없거나 부족한 이유 -->

## 추가할 테스트 케이스
- [ ] 
- [ ] 
- [ ] 

## 검증할 불변 조건
<!-- jsDB 특성상 "결정적 결과"보다 "불변 조건" 위주로 -->
```kotlin
// e.g.
// tree.scanAll() 결과가 항상 정렬되어 있다
// validateInvariants() == true
// recordCount == 슬롯 배열 길이
```

## 파일 위치
- `src/test/kotlin/...` <!-- 신규: (신규) / 수정: (수정) -->

## 의존 Issue
<!-- 선행되어야 하는 Issue가 있으면 -->
- 

## 완료 기준
- [ ] 테스트 전부 green
- [ ] 테스트 간 격리 확인 (`@TempDir` 또는 `beforeTest` 독립 인스턴스)