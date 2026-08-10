## PostgreSQL — 타입별 비교 함수를 쓰기 때문에 정렬 가능한 바이트 인코딩이 필요 없다

> [MySQL/encoding.md](../MySQL/encoding.md)에서 본 InnoDB는 "바이트를 그대로 비교(memcmp)해도 값의 대소 관계와 일치하도록" 미리 인코딩해뒀다.
> PostgreSQL은 이 전제 자체가 없다
>
> - B-tree 비교가 항상 **타입별 비교 함수 호출**을 거치기 때문에, 저장 형식이 애초에 byte-comparable일 필요가 없다.

### `_bt_compare` — 항상 opclass의 비교 함수를 호출한다

[nbtsearch.c](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/nbtsearch.c#L691-L768)

```c
// nbtsearch.c — _bt_compare()
result = DatumGetInt32(FunctionCall2Coll(&scankey->sk_func,
                                         scankey->sk_collation,
                                         datum, tupdatum));
```

`scankey->sk_func`는 인덱스를 만들 때 선택된 **연산자 클래스(opclass)**가 제공하는 비교 함수 포인터다(정수면 `btint4cmp`, 텍스트면 `bttextcmp` 등). 즉 B-tree가 페이지 위 두 값을 비교할 때마다 **"이 타입은 이렇게 비교한다"는 함수를 실제로 호출**한다 — 바이트를 직접 비교하지 않는다.

> 이게 InnoDB와의 근본적인 차이다.
> InnoDB나 jsDB는 "바이트 비교 = 값 비교"가 되도록 저장 전에 인코딩을 손봐야 하지만(부호 비트 반전, escape 등), PostgreSQL은 **비교 로직 자체를 타입에 위임**하니 저장 형식은 그냥 "이 타입의 자연스러운 메모리 표현"이면 충분하다.
> 정수는 그냥 C의 `int32_t` 그대로, 부호 비트를 반전할 필요가 없다
>
> - `btint4cmp` 함수가 부호 있는 정수 비교를 정확히 알아서 해주기 때문이다.

### 대가 — 매 비교마다 함수 호출 오버헤드

이 방식의 트레이드오프는 명확하다.

- memcmp 한 번이면 끝날 비교가 **함수 포인터를 통한 호출**(`FunctionCall2Coll`)이 되어, 비교가 잦은 정렬/탐색 경로에서 상대적으로 비용이 크다.
- PostgreSQL은 이 비용을 줄이기 위해 별도의 최적화(정렬 시 "abbreviated key" - 텍스트 등 비교 비용이 큰 타입에 한해 앞부분만 잘라 memcmp 가능한 근사 표현을 미리 만들어두고, 그걸로 1차 비교 후 필요할 때만 진짜 비교 함수를 호출)를 두고 있다.
- 반대로 B-tree 인덱스 자체의 페이지 내 탐색에는 이 최적화가 적용되지 않고, 매번 opclass 비교 함수를 그대로 호출한다.

### 가변 길이 값 — varlena, 1바이트 또는 4바이트 길이 헤더

PostgreSQL의 가변 길이 타입(`text`, `bytea` 등)은 **varlena**로 저장된다

- 값 앞에 길이 헤더가 붙는데, 짧은 값(127바이트 이하, 비압축)은 1바이트 헤더, 그 외에는 4바이트 헤더를 쓴다(TOAST로 별도 저장되는 큰 값은 또 다른 얘기).
- InnoDB가 레코드 헤더에 길이 목록을 모아두는 것과 달리, PostgreSQL은 **각 값 앞에 그 값 자신의 길이를 바로 붙인다**

### 참고 자료

- [nbtsearch.c](https://github.com/postgres/postgres/blob/1200dfd60c367e97a5e1f31100d82072eb0178b6/src/backend/access/nbtree/nbtsearch.c) — `_bt_compare`
