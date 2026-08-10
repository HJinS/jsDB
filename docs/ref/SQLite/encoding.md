## SQLite — 공식 문서화된 Serial Type + Varint 레코드 포맷

> [MySQL/encoding.md](../MySQL/encoding.md), [PostgreSQL/encoding.md](../PostgreSQL/encoding.md)와 짝을 이루는 절. SQLite는 셋 중 유일하게 레코드 바이트 포맷을 **공식 문서로 상세히 공개**하고 있다([Database File Format](https://sqlite.org/fileformat2.html)) — 지난번 [page.md](./page.md)에서 본 것과 같은 문서다.

### 레코드 = 헤더(serial type 목록) + 바디(실제 값)

> "The header begins with a single varint which determines the total number of bytes in the header... Following the size varint are one or more additional varints, one per column."
> "The values for each column in the record immediately follow the header."

레코드는 두 부분으로 나뉜다.

- **헤더**(각 컬럼의 타입+길이를 나타내는 "serial type" varint들의 목록)
- **바디**(실제 값들이 헤더 순서대로 이어 붙은 것).
  InnoDB가 "레코드 헤더에 가변 길이 목록"을 두는 것과 비슷한 발상이지만, SQLite는 **길이뿐 아니라 타입 정보까지** 이 헤더에 담는다는 점이 다르다.

### Serial Type 코드표

| Serial Type | 크기     | 의미                                          |
| ----------- | -------- | --------------------------------------------- |
| 0           | 0        | NULL                                          |
| 1           | 1        | 8-bit 정수                                    |
| 2           | 2        | 빅엔디안 16-bit 정수                          |
| 3           | 3        | 빅엔디안 24-bit 정수                          |
| 4           | 4        | 빅엔디안 32-bit 정수                          |
| 5           | 6        | 빅엔디안 48-bit 정수                          |
| 6           | 8        | 빅엔디안 64-bit 정수                          |
| 7           | 8        | IEEE 754 64-bit 부동소수점                    |
| 8           | 0        | 정수 0(값 자체가 헤더에 내장, 별도 바디 없음) |
| 9           | 0        | 정수 1(위와 동일한 방식)                      |
| N≥12, 짝수  | (N-12)/2 | BLOB                                          |
| N≥13, 홀수  | (N-13)/2 | 텍스트 문자열                                 |

> 흥미로운 점 두 가지
>
> - **정수는 실제 값의 크기에 맞춰 1~8바이트 중 가장 작은 폭으로 저장**된다(작은 값은 작은 공간.)
> - **정수 0과 1은 바디 자체가 없이 헤더의 serial type 번호만으로 표현**된다(가장 흔한 값이니 아예 공간을 안 쓰는 극단적 최적화).

### 비교는 여기서도 타입별 — memcmp가 아니다

- [vdbeaux.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/vdbeaux.c#L4130)의 `sqlite3VdbeSerialGet()`이 레코드를 읽을 때마다 serial type을 보고 값을 **타입이 있는 `Mem` 구조체로 역직렬화**한 뒤 비교한다(`sqlite3VdbeRecordCompare`).
- PostgreSQL의 opclass 비교 함수 호출과 같은 진영이다 - **저장 바이트를 그대로 비교하지 않고, 항상 해석(deserialize) 후 타입에 맞게 비교**한다.
  정수는 정수끼리, 실수는 실수끼리, 텍스트는 collation에 따라 비교하는 로직이 별도로 있고, NULL < 숫자 < 텍스트 < BLOB라는 타입 간 순서도 비교 함수가 직접 정의한다.

### 참고 자료

- [Database File Format — Record Format](https://sqlite.org/fileformat2.html)
- [vdbeaux.c](https://github.com/sqlite/sqlite/blob/db30b1cbc37979d5a580e14f944680a792b412dd/src/vdbeaux.c)
