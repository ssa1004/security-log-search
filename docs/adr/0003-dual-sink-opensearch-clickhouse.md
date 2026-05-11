# ADR-0003 OpenSearch + ClickHouse 듀얼 sink

- 상태: Accepted
- 날짜: 2026-04-24

## 맥락

본 시스템은 두 가지 다른 종류의 query 를 동시에 잘 처리해야 한다.

1. 운영자의 ad-hoc 검색 — "user=alice 인 인증 실패 이벤트, 최근 1시간". full-text 검색,
   필드 자동완성, facet (terms aggregation), drill-down. 결과 카운트는 작음 (수백~수만).
2. 시계열 / 집계 — "5분 단위 인증 실패 카운트, source.ip 별 top 10, 최근 24시간".
   대용량 GROUP BY, percentile (p95/p99), 사전집계 활용. 결과 카운트는 수백 (bucket 수).

## 검토한 대안

1. OpenSearch 만 — full-text 검색은 강하나 대규모 aggregation 비용이 큼. percentile_bucket
   같은 sub-aggregation 은 메모리 폭발 위험.
2. ClickHouse 만 — 대용량 aggregate 와 percentile 은 압도적이나 free-text 검색이 약함.
   inverted index 가 없고 LIKE 는 full table scan.
3. 둘 다 — 듀얼 sink. 디스크는 두 배지만 query 별로 적합한 엔진을 쓸 수 있음.
4. ClickHouse + 별도 검색 (Tantivy 같은 임베디드) — 운영 부담이 큼.

## 결정

대안 3 (듀얼 sink) 채택. 입력 흐름:

```
events.normalized (Kafka)
  ├── OpenSearch indexer (write alias) — full-text 검색용
  └── ClickHouse Kafka engine 또는 batch loader — 집계용
```

ClickHouse 적재는 두 가지 방식 검토:

- ClickHouse Kafka table engine — 단순하지만 backpressure 제어 어려움
- 본 시스템 자체 batch loader — 미세 제어 가능

본 시스템의 1차 구현은 Kafka engine 사용 (운영 단순). 2차에서 backpressure 가 필요하면
loader 로 전환.

검색 / 집계 라우팅은 application layer 에서 명확히 갈래:

- `SearchLogEventsUseCase` → OpenSearch
- `AggregateLogStatsUseCase` → ClickHouse

## 결과

- 검색 latency p95 < 500ms (OpenSearch), 집계 latency p95 < 2s (ClickHouse 사전집계 MV)
- 디스크 사용량 약 1.8x (압축율 차이로 정확히 2x 는 아님)
- 보존 정책은 두 엔진에 별도 적용 (OpenSearch ILM + ClickHouse TTL)
- 데이터 일관성 윈도우 — Kafka publish 시점 기준 보통 5초 이하 (둘 다 수신)

## 다시 검토할 시점

- ClickHouse 의 vector / ANN search 발전 시 단일화 가능성 검토
- 하루 < 100GB 작은 환경이면 OpenSearch 만으로도 충분할 수 있음 — 운영 비용 트레이드 오프 재계산

## 참고

- [ClickHouse vs Elasticsearch](https://clickhouse.com/blog/clickhouse-vs-elasticsearch)
- [OpenSearch aggregations 메모리 가드](https://opensearch.org/docs/latest/aggregations/)
