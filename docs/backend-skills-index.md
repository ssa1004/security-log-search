# 백엔드 스킬 인덱스 — 이 레포에서 무엇을 배우나

> 이 레포(SIEM 보안 로그 수집/검색/알람 플랫폼)가 시연하는 백엔드 / 데이터 인프라 패턴을
> **"무엇 → 이 레포 어디서 → 왜(ADR) → 더 깊은 이론(dev-lab)"** 으로 잇는 학습용 인덱스.
> "이 패턴 공부하려면 어디부터 보나"의 진입점. 설명을 다시 쓰지 않고 코드·결정·이론으로 연결만 한다.

도메인: 다양한 source(방화벽 / EDR / 시스템 / 응용 / CloudTrail / K8s audit)의 raw event 를
ECS / OCSF 로 정규화 → Kafka 수집 → OpenSearch(검색) + ClickHouse(집계) 듀얼 sink →
Flink 로 실시간 상관 룰(Sigma 포함) 평가 → 알람. 멀티테넌트 4-layer 격리 + ISMS-P 통제 매핑.

## 검색 · 역색인 (OpenSearch)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **full-text 검색 + facet(terms agg)** | `SearchLogEventsUseCase` → OpenSearch | [ADR-0003](adr/0003-dual-sink-opensearch-clickhouse.md) | Lucene query string + filter + facet + cursor pagination |
| **ILM + write/read alias swap** | admin endpoint, `ManageOpenSearchIndexUseCase` | [ADR-0006](adr/0006-opensearch-ilm-alias.md) | rollover(size 50GB / age 30d) 자동 → hot/warm/cold/delete tier |
| **인덱스 이름에 tenant 박기** | `events-{tenant}-{seq}` + alias | [ADR-0006](adr/0006-opensearch-ilm-alias.md), [ADR-0007](adr/0007-multi-tenant-isolation.md) | application layer 는 alias 만 노출 (격리 layer 1) |

→ 이론: `dev-lab/opensearch` (역색인 / 분석기 / aggregation 메모리 가드 / ILM)

## 컬럼형 OLAP (ClickHouse)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **MergeTree + 월별 partition + TTL** | `events_raw` 스키마 (security-bootstrap `clickhouse/`) | [ADR-0005](adr/0005-clickhouse-schema.md) | `PARTITION BY toYYYYMM` + `ORDER BY (tenant_id, timestamp, event_id)` + 13개월 TTL DROP |
| **사전집계 materialized view** | `events_5m_mv` 등 → `AggregateLogStatsUseCase` | [ADR-0005](adr/0005-clickhouse-schema.md) | 운영 query 가 raw 대신 5분/1시간 집계 결과만 읽음 (p95 < 500ms) |
| **압축 코덱 / LowCardinality / skip index** | `CODEC(Delta, ZSTD)`, `bloom_filter` | [ADR-0005](adr/0005-clickhouse-schema.md) | 시계열 압축 4-5x + high-cardinality 점조회 가속 |
| **Row Policy (row-level security)** | `WHERE tenant_id = currentSetting('tenant_id')` | [ADR-0007](adr/0007-multi-tenant-isolation.md) | DB engine 차원 강제 격리 (layer 2) |

→ 이론: `dev-lab/clickhouse` (컬럼형 / MergeTree / materialized view / partition / TTL)

## 수집 파이프라인 (Kafka)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **정규화 후 Kafka 발행** | `IngestLogEventUseCase` → `events.normalized` | [ADR-0004](adr/0004-flink-vs-kafka-streams.md) | raw → ECS/OCSF 정규화 → publish → 듀얼 sink fan-out |
| **idempotent producer + Idempotency-Key** | producer 설정 + `idempotency_keys` 테이블 | [ADR-0004](adr/0004-flink-vs-kafka-streams.md) | 중복 ingest 방지 (at-least-once 의 중복 멱등 처리) |
| **consumer poll backpressure** | `alerts.fired` consumer (`max-poll-records` / `ack-mode: RECORD`) | [ADR-0009](adr/0009-backpressure.md) | 처리 못 하면 다음 poll 미뤄짐 — 메모리 폭발 방지 |
| **dual-listener (host vs container)** | `infrastructure/docker/docker-compose.yml` | — | 호스트 `localhost:29092` / 컨테이너 `kafka:9092` (EXTERNAL/PLAINTEXT) |

→ 이론: `dev-lab/kafka` (수집 파이프라인 / 전달 의미 / consumer group / listener)

## 스트림 처리 · Sigma 매칭 (Flink)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **Flink vs Kafka Streams 선택** | `security-streaming` 모듈 (별도 jar) | [ADR-0004](adr/0004-flink-vs-kafka-streams.md) | hot reload + SEQUENCE 매칭 + exactly-once → Flink |
| **KeyedProcessFunction + MapState** | `CorrelationProcessFunction`, `RuleEvaluator` | [ADR-0008](adr/0008-alert-rule-engine.md) | 슬라이딩 윈도우 카운트 누적 → THRESHOLD / SEQUENCE 평가 |
| **Broadcast state 룰 hot reload** | `AlertCorrelationJob` (`KeyedBroadcastProcessFunction`) | [ADR-0008](adr/0008-alert-rule-engine.md) | Postgres→reader→Kafka broadcast→Flink, job 재시작 없이 < 5초 반영 |
| **event time / window / Timer state cleanup** | `CorrelationProcessFunction` (Flink Timer) | [ADR-0008](adr/0008-alert-rule-engine.md) | 윈도우 만료 시 state 정리 — 무한 누적 방지 |
| **Flink 자체 backpressure** | source→operator→sink 버퍼 | [ADR-0009](adr/0009-backpressure.md) | downstream 느리면 upstream poll 자동 정지, monitoring 으로 병목 식별 |

→ 이론: `dev-lab/flink` (스트림 처리 / event time / window / keyed & broadcast state / CEP)

## 정규화 · 스키마 매핑

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **ECS / OCSF 듀얼 매핑** | `OcsfNormalizer` (OCSF→ECS), 도메인은 ECS | [ADR-0002](adr/0002-ecs-vs-ocsf.md) | 도메인 모델은 ECS 일관, OCSF source 만 변환 |
| **source 별 매퍼 분리 + 라우팅** | `mapping/source/` (CloudTrail / K8s audit), `RoutingNormalizer` | [ADR-0014](adr/0014-source-adapter-cloudtrail-k8s.md) | schema 힌트로 매퍼 라우팅 — 새 source 추가가 클래스 추가로 끝남 |
| **Sigma 룰 import → AlertRule 변환** | `ImportSigmaRuleUseCase`, `SigmaYamlParser`, `SigmaToAlertRuleMapper` | [ADR-0013](adr/0013-sigma-rule-import.md) | 외부 인텔리전스(SigmaHQ) 당일 수용, unsupported 는 disabled 로 안전 default |

→ 이론: `dev-lab/system-design` (어댑터 / anti-corruption layer), `dev-lab/distributed-systems` (멱등 / at-least-once)

## 멀티테넌시 격리 (defense-in-depth)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **4-layer 격리** | layer 1 alias / 2 Row Policy / 3 JWT claim / 4 query rewrite | [ADR-0007](adr/0007-multi-tenant-isolation.md) | 한 layer 깨져도 나머지가 막음 (보안 로그 leak = 추가 공격 표면) |
| **JWT claim → OperatorContext** | `OperatorContextResolver` (claim.tenant_id) | [ADR-0007](adr/0007-multi-tenant-isolation.md) | 모든 요청에 tenant 강제, query 의 1차 keying |
| **query rewrite 강제 주입** | `SearchLogEventsService` (BoolQuery filter / SQL param) | [ADR-0007](adr/0007-multi-tenant-isolation.md) | application layer 가 tenant filter 를 항상 AND 결합 |

→ 이론: `dev-lab/system-design` (멀티테넌시 격리 모델 — pool vs silo vs bridge), `dev-lab/distributed-systems`

## 회복탄력성 (Resilience)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **CircuitBreaker + Retry + Bulkhead** | OpenSearch / ClickHouse adapter (Resilience4j) | [ADR-0009](adr/0009-backpressure.md) | 일시 장애 시 fail-fast(10s) + 동시 호출 상한으로 메모리 폭발 방지 |

→ 이론: `dev-lab/distributed-systems` (circuit breaker / bulkhead / backpressure)

## 보안 · 컴플라이언스 (ISMS-P)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **append-only audit log** | `audit_entries` (INSERT only, UPDATE/DELETE 차단) | [ADR-0011](adr/0011-audit-log-append-only.md) | 모든 운영자 행위 1:1 row, 무결성 + 5년 보존 |
| **PII 마스킹 + 보존 정책** | tenant `piiPolicy`(STRICT) + 마스킹 | [ADR-0012](adr/0012-pii-masking-retention.md) | 개인정보 마스킹 + tenant 별 보존 차등 |
| **ISMS-P 통제 매핑** | 2.5 인증 / 2.7 암호화 / 2.9 감사 / 2.10 사고대응 | [ADR-0010](adr/0010-isms-p-control-mapping.md) | 인증 요구를 시스템 구현으로 1:1 매핑 |

→ 이론: `dev-lab/system-design` (감사 / WORM storage), `dev-lab/observability` (audit ≠ log 구분)

## 운영 · 관측성 (Observability)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **RED + USE 대시보드** | `docs/adr/0015`, observability provisioning | [ADR-0015](adr/0015-observability-dashboards.md) | 수집/검색/스트리밍 각 구간의 RED + 인프라 USE |
| **런북 (알람 → 대응 절차)** | `docs/runbook/` (ingest drop / flink lag / alert storm) | [ADR-0015](adr/0015-observability-dashboards.md) | 시나리오별 "어디부터 보나" 절차서 |
| **SIEM 특화 부하 시나리오** | `load/k6/scenarios/` (5종) | — | RPS 가 아닌 ingestion / Lucene / facet / 격리 invariant / Sigma→Flink latency |

→ 이론: `dev-lab/observability` (3축 + SLI/SLO + RED/USE), `dev-lab/distributed-systems` (수집 파이프라인 운영)

## 학습 순서 제안 (이 레포 기준)

1. **[README](../README.md) 처리 흐름 + 모듈 구조** → 수집→정규화→듀얼 sink→Flink→알람 전체 그림
2. **[docs/adr/](adr/)** → 왜 그렇게 했나 (ADR 15개) ← 이 레포의 핵심 학습 자료
   - 먼저 [ADR-0001](adr/0001-hexagonal-architecture.md)(헥사고날 모듈 분리) → [ADR-0003](adr/0003-dual-sink-opensearch-clickhouse.md)(왜 엔진 2개)
3. **검색/OLAP 갈래** → [ADR-0005](adr/0005-clickhouse-schema.md)(ClickHouse) · [ADR-0006](adr/0006-opensearch-ilm-alias.md)(OpenSearch) + `dev-lab/clickhouse`, `dev-lab/opensearch`
4. **스트리밍 갈래** → [ADR-0004](adr/0004-flink-vs-kafka-streams.md) · [ADR-0008](adr/0008-alert-rule-engine.md)(Flink broadcast state) + `dev-lab/flink`, `dev-lab/kafka`
5. **격리/보안 갈래** → [ADR-0007](adr/0007-multi-tenant-isolation.md)(4-layer) · [ADR-0010](adr/0010-isms-p-control-mapping.md)(ISMS-P) + `dev-lab/system-design`
6. **운영 관점** → [docs/runbook/](runbook/) + [ADR-0015](adr/0015-observability-dashboards.md) + `make demo` 로 Sigma→alert 흐름 직접 시연

> 짝 학습 레포: [dev-lab](https://github.com/ssa1004/dev-lab) (이론) ↔ 이 레포 (구현).
> 이론에서 "왜"를, 여기서 "실제로 어떻게"를 본다.
