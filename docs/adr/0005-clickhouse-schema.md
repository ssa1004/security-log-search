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

## 용어 풀이 (쉽게)

- **MergeTree** — ClickHouse의 기본 저장 방식. 데이터를 정해진 순서로 정렬·압축해 차곡차곡 쌓고, 작은 조각들을 뒤에서 합쳐(merge) 큰 덩어리로 정리한다(메모를 늘 정해진 순서로 끼워 넣는 바인더).
- **월별 partition(파티션)** — 데이터를 '월' 같은 칸막이로 미리 나눠 담는 것. 특정 달만 조회하면 그 칸만 열어 빠르고, 13개월 지난 칸은 통째로 버리면 폐기도 한순간.
- **materialized view(사전집계 MV)** — 자주 쓰는 통계(5분 단위 카운트)를 미리 계산해 따로 저장한 '요약본'. 조회 때 수억 줄을 다시 세지 않고 요약본만 읽어 빠르다(매번 영수증 다 세지 않고 일별 합계를 미리 적는 가계부).
- **projection(프로젝션)** — 같은 데이터를 '다른 정렬·다른 묶음'으로 미리 한 벌 더 만들어 두고, 쿼리에 맞는 걸 ClickHouse가 알아서 골라 쓰게 하는 기능(같은 명단의 가나다순·나이순 사본을 함께 두는 셈).
- **LowCardinality(저카디널리티)** — 값 종류가 적은 칸(회사명·성공/실패)을 '사전 번호'로 바꿔 저장해 용량을 줄이고 비교를 빠르게 하는 것. 긴 이름 대신 짧은 번호표를 붙이는 셈.
- **압축 코덱(CODEC, ZSTD / Delta)** — 데이터를 작게 눌러 담는 방식. ZSTD는 일반 압축, Delta는 '앞 값과의 차이만 적기'라 시각처럼 조금씩 증가하는 값에 특히 효율이 좋다.
- **bloom_filter skip index** — '이 덩어리에 이 값이 있을 가능성이 있나'만 빠르게 걸러, 없는 게 확실한 덩어리는 통째로 건너뛰게 해 주는 가벼운 색인(상자를 다 안 열고 '이 안엔 없음' 라벨만 보는 격).
- **sparse index(희소 색인)** — 모든 줄이 아니라 '8192줄마다 하나'씩 듬성듬성 표시를 둔 색인. 표시를 보고 대략 구간으로 점프한 뒤 그 안만 훑어, 색인이 가벼우면서도 시계열 조회가 빠르다.
- **sharded cluster / ReplicatedMergeTree** — sharded는 데이터를 여러 서버에 쪼개 나눠 담아 처리를 분산하는 것, Replicated는 같은 데이터를 여러 벌 복제해 한 서버가 죽어도 버티게 하는 것.
- **ANN search(근사 최근접 검색)** — '뜻이 비슷한 것'을 벡터 거리로 빠르게 찾는 검색. 정확한 1등을 끝까지 따지는 대신 '거의 가까운' 후보를 빨리 골라 의미 기반 검색에 쓴다.

## 참고

- [ClickHouse MergeTree docs](https://clickhouse.com/docs/en/engines/table-engines/mergetree-family/mergetree)
- [ClickHouse Materialized View](https://clickhouse.com/docs/en/sql-reference/statements/create/view)
