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

## 용어 풀이 (쉽게)

- **backpressure(배압)** — 받는 쪽이 처리 못 할 만큼 일이 밀려올 때 '천천히 보내'라는 신호를 보내는 쪽으로 거슬러 전달하는 자동 제동(좁은 깔때기에 물을 넘치지 않게 붓는 속도 조절).
- **Kafka consumer poll / max-poll-records** — 소비자가 큐에서 일감을 한 움큼씩 꺼내 오는(poll) 동작. max-poll-records는 '한 번에 몇 건까지 집어 올지'의 상한이라, 처리 못 할 만큼 욕심내 가져오는 걸 막는다.
- **ack-mode RECORD(레코드 단위 확인)** — 메시지를 한 건 처리할 때마다 '여기까지 끝' 도장을 찍는 방식. 묶음으로 뒤늦게 찍으면 장애 시 재처리할 양이 커지므로, 건건이 찍어 재시도 비용을 줄인다.
- **group rebalance(그룹 재배분)** — 소비자가 들고날 때 '누가 어느 큐 조각을 맡을지' 다시 나누는 과정. 처리가 밀리면 다음 poll이 자동으로 미뤄지며 이 재배분까지 기다린다.
- **서킷 브레이커(Circuit Breaker)** — 외부 호출이 자꾸 실패하면 두꺼비집처럼 잠시 회선을 끊어 즉시 실패시키고, 죽은 서버를 계속 두드려 같이 쓰러지는 걸 막는 것.
- **Bulkhead(격벽 격리)** — 배의 방수 격벽처럼 외부 호출을 전용 풀에 가둬 동시 호출 수에 상한을 두는 것. 한 곳이 느려져도 메모리·스레드를 다 잡아먹어 전체가 무너지는 걸 막는다.
- **Retry + 지수 백오프** — 일시적 실패는 정해진 횟수만큼 다시 시도하되 간격을 1→2→4초처럼 점점 늘려, 회복 중인 서버에 동시에 다시 몰리지 않게 하는 것.
- **fail-fast(빠른 실패)** — 죽은 줄 아는 상대를 타임아웃까지 기다리지 않고 곧바로 실패로 돌려주는 것. 회로가 끊긴(OPEN) 동안엔 시도조차 안 해 자원을 아끼고 운영자가 즉시 인지한다.

## 참고

- [Resilience4j docs](https://resilience4j.readme.io/)
- [Flink Backpressure](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/ops/monitoring/back_pressure/)
