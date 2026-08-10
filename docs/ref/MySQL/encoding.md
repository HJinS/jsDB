## InnoDB — memcmp으로 비교 가능한 바이트 인코딩

> InnoDB는 B-tree 키를 비교할 때 타입별 비교 함수를 따로 호출하지 않고, **저장된 바이트 자체를 그대로 비교(사실상 memcmp)해서 순서를 판단한다.**
> 그래서 원래 값의 대소 관계가 바이트 순서와 정확히 일치하도록, 저장 시점에 미리 "정렬 가능한(byte-comparable)" 형태로 인코딩해둬야 한다.

### 정수 — 빅엔디안 + 부호 비트 반전

[row0mysql.cc](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/row/row0mysql.cc#L416-L436)

```cpp
// row0mysql.cc:416-436
if (type == DATA_INT) {
  /* Store integer data in Innobase in a big-endian format,
  sign bit negated if the data is a signed integer. In MySQL,
  integers are stored in a little-endian format. */

  byte *p = buf + col_len;
  for (;;) {
    p--;
    *p = *mysql_data;
    if (p == buf) break;
    mysql_data++;
  }

  if (!(dtype->prtype & DATA_UNSIGNED)) {
    *buf ^= 128;      // 최상위 바이트의 부호 비트만 반전
  }
  ...
}
```

같은 파일 다른 위치의 주석 이유까지 명시한다.

[row0mysql.cc:823-829](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/row/row0mysql.cc#L823-L829)

```
MySQL stores INTs in little endian format and InnoDB stores INTs in
big endian format with the sign bit flipped. All other field types
are stored/compared the same in MySQL and InnoDB.
```

빅엔디안으로 바꾸는 건 "바이트를 앞에서부터 비교했을 때 큰 자리수부터 비교되게" 하기 위함이고, 부호 비트 반전(`^= 128`, 즉 최상위 비트 XOR)은 음수/양수가 섞였을 때도 순서가 깨지지 않게 하기 위함이다 — 2의 보수 표현에서는 음수의 최상위 비트가 1이라 그냥 두면 음수가 양수보다 "바이트 값이 커서" 뒤로 밀리는 문제가 생기는데, 부호 비트를 반전시키면 이 문제가 사라진다.

### 가변 길이 필드 — 레코드 헤더에 길이 목록을 별도로 둔다

[rem0rec.cc:95-139 — PHYSICAL RECORD (NEW STYLE)](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/rem/rem0rec.cc#L95-L139)

```
| length of the last non-null variable-length field of data: ... |
...
| length of first variable-length field of data |
| SQL-null flags (1 bit per nullable field), padded to full bytes |
...
ORIGIN of the record
| first field of data |
...
| last field of data |
```

InnoDB의 compact 레코드 포맷은 실제 값 앞에 길이를 인라인으로 안 두고, **레코드 헤더(origin 바로 앞)에 모든 가변 길이 필드의 길이를 모아서 역순으로 저장**한다(필드당 1~2바이트, 고정 길이 필드는 목록에 아예 안 들어감).
NULL인 nullable 컬럼은 이 길이 목록이 아니라 헤더의 별도 **null flags 비트맵(nullable 컬럼당 1비트, 바이트 단위로 패딩)**으로 표시된다

### 참고 자료

- [row0mysql.cc](https://github.com/mysql/mysql-server/blob/06a5c1c99c377fc41b2eba1ea244e8b220bdc3c8/storage/innobase/row/row0mysql.cc)
