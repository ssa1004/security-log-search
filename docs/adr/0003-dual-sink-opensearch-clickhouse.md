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

## 용어 풀이 (쉽게)

- **듀얼 sink(dual-sink) + OpenSearch / ClickHouse** — 같은 로그를 성격이 다른 두 저장소에 동시에 적재. OpenSearch는 '구글 검색'처럼 단어 찾기에 강하고, ClickHouse는 '엑셀 피벗'을 수억 줄에서 돌리는 집계 전용. 찾기는 전자, 합계·순위는 후자.
- **full-text 검색** — 글 속 단어로 찾는 검색. 문서 전체를 미리 단어별로 색인해 둬서 '로그인 실패' 같은 말로 바로 찾는다(책 뒤 '찾아보기'로 단어 페이지 잡기).
- **inverted index(역색인)** — '어느 단어가 어느 문서에 있나'를 거꾸로 정리해 둔 목록. 문서를 한 장씩 넘기지 않고 단어로 곧장 점프하게 해 준다. ClickHouse엔 이게 없어 단어 찾기가 약하다.
- **facet / aggregation(집계)** — 결과를 '브랜드별 개수'처럼 묶어 세는 것. 검색 결과를 카테고리별 막대그래프로 요약해 주는 셈.
- **percentile (p95/p99)** — '응답 시간 줄을 세웠을 때 95%·99% 지점 값'. 평균은 빠른 몇 건에 가려지지만, 이건 '느린 쪽 손님 대부분이 이 정도 안에는 받았다'를 보여 준다.
- **full table scan(풀 스캔)** — 알맞은 색인이 없어 데이터 전체를 처음부터 끝까지 다 훑는 것. 전화번호부를 첫 장부터 한 명씩 넘기는 격이라 느리다.
- **materialized view(사전집계 MV)** — 자주 쓰는 통계(5분 단위 카운트)를 미리 계산해 따로 저장한 '요약본'. 조회 때 수억 줄을 다시 세지 않고 요약본만 읽어 빠르다(매번 영수증 다 세지 않고 일별 합계를 미리 적는 가계부).
- **ad-hoc 검색** — 미리 정해 둔 게 아니라 그때그때 즉석으로 던지는 자유 검색('지금 이 사용자 최근 1시간 실패 보여줘').
- **보존 정책(ILM / TTL)** — 오래된 데이터를 정해진 기간만 두고 자동으로 폐기하는 규칙. ILM은 OpenSearch 쪽, TTL은 ClickHouse 쪽의 '유통기한 자동 폐기' 장치.

## 참고

- [ClickHouse vs Elasticsearch](https://clickhouse.com/blog/clickhouse-vs-elasticsearch)
- [OpenSearch aggregations 메모리 가드](https://opensearch.org/docs/latest/aggregations/)
