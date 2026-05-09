-- ClickHouse 초기 스키마 — 운영 시 별도 IaC (Terraform / Helm) 또는 운영자 DDL 로 적용.
-- 본 파일은 docs/operations.md 의 참고 SQL.
--
-- 스키마 설계 (ADR-0005):
--   - MergeTree + PARTITION BY toYYYYMM(timestamp) — 월별 partition, 이전 데이터 DROP 빠름
--   - ORDER BY (tenant_id, timestamp, event_id) — tenant + 시계열 query 효율
--   - SETTINGS index_granularity = 8192 (default)
--   - 컬럼별 ZSTD 압축
--   - 5분 / 1시간 사전집계 MaterializedView (events_5m_mv, events_1h_mv)

CREATE TABLE IF NOT EXISTS events_raw (
    event_id          UUID,
    tenant_id         LowCardinality(String) CODEC(ZSTD(3)),
    timestamp         DateTime64(3, 'UTC') CODEC(Delta(8), ZSTD(3)),
    ingested_at       DateTime64(3, 'UTC') CODEC(Delta(8), ZSTD(3)),
    event_kind        LowCardinality(String) CODEC(ZSTD(3)),
    event_category    LowCardinality(String) CODEC(ZSTD(3)),
    event_type        LowCardinality(String) CODEC(ZSTD(3)),
    event_action      LowCardinality(String) CODEC(ZSTD(3)),
    event_outcome     LowCardinality(String) CODEC(ZSTD(3)),
    severity          LowCardinality(String) CODEC(ZSTD(3)),
    source_ip         IPv6 CODEC(ZSTD(3)),
    source_port       UInt16 CODEC(ZSTD(3)),
    destination_ip    IPv6 CODEC(ZSTD(3)),
    destination_port  UInt16 CODEC(ZSTD(3)),
    user_name         String CODEC(ZSTD(3)),
    host_name         LowCardinality(String) CODEC(ZSTD(3)),
    host_os           LowCardinality(String) CODEC(ZSTD(3)),
    message           String CODEC(ZSTD(3)),
    labels            Map(String, String) CODEC(ZSTD(3)),
    INDEX idx_user_name user_name TYPE bloom_filter GRANULARITY 4,
    INDEX idx_event_action event_action TYPE bloom_filter GRANULARITY 4
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tenant_id, timestamp, event_id)
TTL toDateTime(timestamp) + INTERVAL 13 MONTH DELETE
SETTINGS index_granularity = 8192;

-- 5분 사전집계.
CREATE MATERIALIZED VIEW IF NOT EXISTS events_5m_mv
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tenant_id, timestamp, event_action, event_outcome, source_ip)
AS SELECT
    tenant_id,
    toStartOfInterval(timestamp, INTERVAL 5 MINUTE) AS timestamp,
    event_action,
    event_outcome,
    source_ip,
    count() AS cnt
FROM events_raw
GROUP BY tenant_id, timestamp, event_action, event_outcome, source_ip;

-- 1시간 사전집계.
CREATE MATERIALIZED VIEW IF NOT EXISTS events_1h_mv
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tenant_id, timestamp, event_action, event_outcome, source_ip)
AS SELECT
    tenant_id,
    toStartOfHour(timestamp) AS timestamp,
    event_action,
    event_outcome,
    source_ip,
    count() AS cnt
FROM events_raw
GROUP BY tenant_id, timestamp, event_action, event_outcome, source_ip;

-- search role + tenant 별 row policy 의 기본 정의 (운영자가 신규 tenant 등록 시 추가).
CREATE ROLE IF NOT EXISTS sec_search_role;
GRANT SELECT ON events_raw TO sec_search_role;
GRANT SELECT ON events_5m_mv TO sec_search_role;
GRANT SELECT ON events_1h_mv TO sec_search_role;
