## PostgreSQL — Clock-Sweep

> Lock/Pin 규칙 자체는 [buffer-pool-manager.md](../../buffer-pool/buffer-pool-manager.md)에 정리되어 있다. 여기서는 victim 선택 알고리즘만 다룬다.

각 버퍼는 `usage counter`를 가지고, pin 될 때마다(상한까지) 증가한다. `nextVictimBuffer`(시계 바늘)가 전체 버퍼를 원형으로 순환하며 victim을 찾는다.

1. `buffer_strategy_lock` 획득
2. `nextVictimBuffer`가 가리키는 버퍼 선택, 바늘 전진 → `buffer_strategy_lock` 릴리즈
3. 선택한 버퍼가 pin 되었거나 usage count > 0이면 사용 불가 → usage count 감소 후 `buffer_strategy_lock` 재획득하여 다음 버퍼로 (스텝 2부터 반복)
4. 사용 가능하면 버퍼 pin 후 반환

**핵심 포인트**
- InnoDB처럼 리스트를 재배치하지 않는다. 원형 포인터 이동 + usage count 감쇠만으로 근사 LRU 효과를 낸다.
- 스캔 저항은 별도의 `Buffer Ring Replacement`(VACUUM/시퀀셜 스캔/대량 쓰기 전용 소형 링 버퍼)로 달성한다. InnoDB처럼 하나의 리스트 안에서 old/young을 나누는 방식이 아니라, **아예 별도의 작은 버퍼 풀을 분리**하는 방식이다.

