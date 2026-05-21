# ADR-0009 Backpressure — Kafka consumer poll + Flink 자체

- 상태: Accepted
- 날짜: 2026-04-30

## 맥락

이벤트 트래픽이 폭주 (예: DDoS 공격 직후 방화벽이 초당 수십만 evt 발생) 하면 다음 단계
어딘가가 병목이 된다.

- Kafka consumer 가 너무 빨리 poll → application layer 에서 OpenSearch 적재가 못 따라감
- Flink job 의 sink (Kafka alerts.fired) 가 느려서 upstream 가 누적
- OpenSearch / ClickHouse 가 일시 장애일 때 Spring Kafka consumer 가 무한 retry

## 검토한 대안

1. Kafka consumer poll 무제한 — 메모리 폭발
2. Application layer 에서 명시 buffer + drop — 데이터 손실
3. Kafka consumer poll 튜닝 + Flink 자체 backpressure + Resilience4j circuit breaker

## 결정

대안 3 (조합). 각 구간별 backpressure 메커니즘:

### Spring Kafka consumer (alerts.fired)

```yaml
security:
  kafka:
    consumer:
      max-poll-records: 200      # 한 번에 가져오는 max 메시지 수
spring:
  kafka:
    listener:
      ack-mode: RECORD            # record 단위 commit (batch 가 길면 retry 비용 큼)
```

처리 못 하면 다음 poll 이 자동으로 미뤄짐 — Kafka 자체의 group rebalance 까지 기다림.

### Flink backpressure

Flink 는 자체 backpressure 가 builtin. downstream operator 가 느리면 buffer 가 채워지면서
upstream operator 가 자동으로 poll 을 멈춤. monitoring 으로 어디서 backpressure 가 생겼는지
확인 가능.

### OpenSearch / ClickHouse 호출 보호

```kotlin
@CircuitBreaker(name = "opensearch")
@Retry(name = "opensearch")
@Bulkhead(name = "opensearch")
override fun search(query: SearchQuery): SearchResult { ... }
```

Resilience4j 설정:

- `slidingWindowSize: 50, failureRateThreshold: 50%` — 최근 50건 중 25건 이상 실패 시
  OPEN
- `waitDurationInOpenState: 10s` — 10초간 호출 안 하고 fail-fast
- `bulkhead.maxConcurrentCalls: 50` — 동시 호출 상한

OpenSearch 가 일시 장애여도 Bulkhead 가 동시 호출 상한을 두니 메모리 폭발 방지.

## 결과

- 단계별 명확한 backpressure — 어느 구간이 병목인지 metric 으로 식별 가능
- OpenSearch 장애 시 fail-fast (10초간) → 운영자가 알람으로 즉시 인지

## 다시 검토할 시점

- 트래픽이 영구적으로 늘어나면 Kafka partition 추가 + consumer concurrency 증가
- 사전집계 MV 가 지연되면 ClickHouse 자체 스케일 (sharding) 검토

## 참고

- [Resilience4j docs](https://resilience4j.readme.io/)
- [Flink Backpressure](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/ops/monitoring/back_pressure/)
