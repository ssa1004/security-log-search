# ADR-0005 ClickHouse 스키마 — MergeTree + 월별 partition + materialized view

- 상태: Accepted
- 날짜: 2026-04-26

## 맥락

ClickHouse 의 스키마 설계는 query 패턴을 미리 알고 잡아야 한다. 본 시스템의 집계 query
는 다음 패턴을 가진다.

- WHERE tenant_id = ? AND timestamp BETWEEN ? AND ?
- GROUP BY toStartOfInterval(timestamp, ?), event_action / source_ip / user_name
- 1주일 / 1개월 단위 trend, top-N source.ip 등

raw 이벤트는 하루 100GB ~ 수 TB 까지 가는 트래픽을 가정.

## 검토한 대안

1. 단일 거대 테이블 (events_raw) + GROUP BY 만 — 1년 데이터에서 5분 bucket query 가
   초당 수억 row scan, latency 폭발.
2. raw + materialized view 사전집계 — 5분 / 1시간 단위 사전집계를 MV 로 두면 운영 query 가
   집계 결과만 읽음.
3. raw + projection — ClickHouse 의 projection 은 query rewrite 가 자동이지만 본 시스템의
   query 패턴이 충분히 정해져 있으니 명시적 MV 가 더 명확.

## 결정

대안 2 채택. 스키마 (요약):

```sql
CREATE TABLE events_raw (
    event_id UUID,
    tenant_id LowCardinality(String) CODEC(ZSTD(3)),
    timestamp DateTime64(3, 'UTC') CODEC(Delta(8), ZSTD(3)),
    ...
    INDEX idx_user_name user_name TYPE bloom_filter GRANULARITY 4
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tenant_id, timestamp, event_id)
TTL toDateTime(timestamp) + INTERVAL 13 MONTH DELETE
SETTINGS index_granularity = 8192;
```

핵심 결정:

- `PARTITION BY toYYYYMM(timestamp)` — 월별 partition. 1년 보존 + 1개월 buffer. 13개월
  지난 partition 은 자동 DROP (TTL).
- `ORDER BY (tenant_id, timestamp, event_id)` — tenant 별 시계열 query 가 sparse index 로
  점프 가능. event_id 는 동일 timestamp 충돌 회피.
- `LowCardinality(String)` for tenant_id / category / outcome / severity — 압축 + 비교
  속도 개선.
- `CODEC(Delta(8), ZSTD(3))` for timestamp — 시계열 압축 효율 극대화.
- `bloom_filter` skip index — user_name / event_action 같은 high-cardinality 컬럼의 점
  조회 가속.
- 5분 / 1시간 사전집계 MV — 운영 query 가 집계 결과만 읽음 (raw 는 1일 bucket 또는 raw
  drill-down 용).

## 결과

- 1일 1TB 트래픽에서 5분 bucket query 가 < 500ms p95
- 압축율 raw 대비 약 4-5x (ZSTD(3) + LowCardinality + Delta)
- 보존 1년 + 1개월 buffer — 13개월 지난 데이터 자동 삭제

## 다시 검토할 시점

- 트래픽이 일 10TB 이상으로 가면 sharded cluster + ReplicatedMergeTree 검토
- vector / ANN search 가 추가로 필요해지면 별도 column 또는 별도 storage 검토
- materialized view 가 너무 많아지면 (10개 이상) 빌드 비용 vs query 비용 재평가

## 참고

- [ClickHouse MergeTree docs](https://clickhouse.com/docs/en/engines/table-engines/mergetree-family/mergetree)
- [ClickHouse Materialized View](https://clickhouse.com/docs/en/sql-reference/statements/create/view)
