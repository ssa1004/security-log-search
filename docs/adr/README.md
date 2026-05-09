# Architecture Decision Records

본 디렉토리는 security-log-search 의 핵심 설계 결정을 시간 순으로 기록한 ADR 모음입니다.
각 문서는 결정 시점, 맥락, 검토한 대안, 채택 이유, 결과의 형태로 정리합니다.

| 번호 | 제목 |
|---|---|
| [0001](0001-hexagonal-architecture.md) | Hexagonal architecture + 모듈 분리 (security-streaming 별도 jar) |
| [0002](0002-ecs-vs-ocsf.md) | ECS vs OCSF — 둘 다 매핑하는 이유 |
| [0003](0003-dual-sink-opensearch-clickhouse.md) | OpenSearch + ClickHouse 듀얼 sink |
| [0004](0004-flink-vs-kafka-streams.md) | Kafka 수집 + Flink 스트리밍 (vs Kafka Streams) |
| [0005](0005-clickhouse-schema.md) | ClickHouse 스키마 — MergeTree + 월별 partition + materialized view |
| [0006](0006-opensearch-ilm-alias.md) | OpenSearch ILM + alias swap + hot/warm/cold tier |
| [0007](0007-multi-tenant-isolation.md) | Multi-tenant 격리 — 4 layer |
| [0008](0008-alert-rule-engine.md) | Alert rule engine — Flink CEP + broadcast state hot reload |
| [0009](0009-backpressure.md) | Backpressure — Kafka consumer poll + Flink 자체 |
| [0010](0010-isms-p-control-mapping.md) | ISMS-P 통제 매핑 |
| [0011](0011-audit-log-append-only.md) | Audit log — append-only PostgreSQL + 보존 5년 |
| [0012](0012-pii-masking-retention.md) | PII 마스킹 + 보존 정책 |
