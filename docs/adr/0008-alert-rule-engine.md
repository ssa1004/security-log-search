# ADR-0008 Alert rule engine — Flink CEP + broadcast state hot reload

- 상태: Accepted
- 날짜: 2026-04-29

## 맥락

운영자는 다음과 같은 룰을 정의해야 한다.

- "같은 IP 에서 5분 안 5회 인증 실패" — THRESHOLD 룰
- "5회 실패 직후 1회 성공" — SEQUENCE 룰 (brute-force 침입 패턴)

룰은 운영자가 REST 로 추가 / 수정 / 삭제 가능해야 하며, 변경 시 Flink job 재시작 없이
즉시 반영되어야 한다 (운영자가 룰을 변경했는데 다음 평가에 적용 안 되면 운영 불편).

## 검토한 대안

1. Flink SQL — Flink 의 SQL API 로 룰을 작성. 운영자가 SQL 을 직접 쓰는 건 부담.
2. Flink CEP (Pattern API) — sequence 매칭이 자연스럽게 표현 가능하나 룰 변경 시 job 재
   submission.
3. KeyedProcessFunction + MapState + Broadcast State — 룰을 broadcast 채널로 보내서 hot
   reload, 평가는 직접 구현. 본 ADR 의 채택안.

## 결정

대안 3 채택.

### 데이터 흐름

```
PostgreSQL alert_rules
  ↓ (변경 감지 — 폴링 또는 LISTEN/NOTIFY)
별도 reader 서비스
  ↓ Kafka topic alert-rules.broadcast
Flink job
  ↓ broadcast()
KeyedBroadcastProcessFunction
  - keyed event stream (tenantId + ruleId + groupKey 로 keyBy)
  - broadcast 룰 stream (모든 키에 fan-out)
```

### 평가 로직

`RuleEvaluator` 가 핵심:

- 슬라이딩 윈도우 안 카운트 누적 (Deque)
- THRESHOLD: 카운트 >= threshold → 즉시 알람
- SEQUENCE: 카운트 도달 시 시각만 기록 → 윈도우 안 trailing success 도착 시 알람
- 윈도우 만료 시 (Flink Timer) state cleanup

### 룰 hot reload

- broadcast state 는 모든 task slot 에서 즉시 갱신됨
- 운영자가 새 룰 추가 → Postgres → reader → Kafka → Flink broadcast → 다음 evt 부터 적용
- 룰 변경 latency 보통 < 5초

## 결과

- 룰 변경 시 Flink job 재시작 없음 (savepoint / restore 도 불필요)
- SEQUENCE 룰을 자연스럽게 표현 — `RuleEvaluator` 가 단위 테스트 가능
- 룰이 < 수천 개면 메모리 부담 없음 (broadcast state)

## 다시 검토할 시점

- 룰이 수만 개로 늘어나면 broadcast 가 비효율 — keyed 룰 + per-tenant 분산 검토
- 운영자가 SQL 로 룰 작성하기 원하면 Flink SQL + CTAS 추가

## 참고

- [Flink Broadcast State Pattern](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/fault-tolerance/broadcast_state/)
- [Flink CEP](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/libs/cep/) — 본 ADR 에서 직접 사용은 안 하지만 SEQUENCE 룰의 모태 개념
