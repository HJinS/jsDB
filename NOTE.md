 Note


#### b-tree
검색
수정
1. 해당 노드를 찾은 뒤 value 만 수정
2. value 에는 index ROWID 만 저장
3. covering index의 경우 index key의 값으로만 처리가 가능한 경우 그 값을 반환하는 것임 
   a. collation 의 경우 무조건 데이터 조회 필요(랜덤 I/O)
4. clustered Index의 경우 다음과 같이 저장됨
   a. key: pk 
   b. value: 나머지 필드 데이터
범위스캔



page -> parser -> cache -> overflowPage
