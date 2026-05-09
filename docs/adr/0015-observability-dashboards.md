# ADR-0015 운영 대시보드 — RED + USE 모델 기반

- 상태: Accepted
- 날짜: 2026-05-09

## 맥락

본 서비스는 다른 시스템의 로그를 수집 / 검색 / 알람으로 변환하는 SIEM. 운영자가 *시스템
자체* 의 건강도와 SLO 를 실시간으로 봐야 한다. 모니터링 항목이 산발적으로 늘면 dashboard 가
결국 안 보게 된다 — 처음부터 *모델 기반* 으로 어떤 지표를 어디에 두는지 정해야 한다.

## 검토한 모델

- **RED** (Tom Wilkie, Weaveworks) — 사용자 facing service 에 적합. Rate / Errors / Duration.
- **USE** (Brendan Gregg, Netflix) — 자원 (인프라) 관측에 적합. Utilization / Saturation / Errors.
- **Four Golden Signals** (Google SRE Book) — Latency / Traffic / Errors / Saturation. RED + USE 의
  결합에 가까움.

## 결정

본 서비스의 두 측면 (API / 인프라) 을 RED 와 USE 로 나눠서 4개 dashboard 로 분리.

### Dashboard 1 — `Ingest Throughput` (RED 의 Rate / Errors)

ingest pipeline 의 healthiness 를 한 화면에서 본다.

| 패널 | 메트릭 | 의도 |
|---|---|---|
| Ingest rate (source 별) | `rate(security_log_ingest_total[5m])` | 어느 source 가 얼마나 들어오는지 |
| Ingest rate (tenant 별) | 같음, group by tenant | 멀티테넌트 부하 분포 |
| Kafka producer rate | `rate(kafka_producer_record_send_total[5m])` | 본 서비스 → Kafka 가 막히는지 |
| OpenSearch index rate | `rate(opensearch_indices_indexing_index_total[5m])` | downstream sink 1 |
| ClickHouse insert rate | `rate(ClickHouseProfileEvents_InsertedRows[5m])` | downstream sink 2 |
| 정규화 실패 | `rate(security_log_normalize_failures_total[5m])` | Errors 측면 |
| 실패 비율 | failures / (ingest + failures) | 한 줄 KPI |

### Dashboard 2 — `Query Latency` (RED 의 Duration)

검색 / 집계 SLO 를 본다.

| 패널 | 메트릭 | 의도 |
|---|---|---|
| OpenSearch p50/p95/p99 | `histogram_quantile(0.99, ...search_latency_seconds_bucket{type="opensearch"})` | full-text 검색 SLO |
| ClickHouse p50/p95/p99 | 같음, type=clickhouse | aggregate SLO |
| tenant 별 p99 | group by tenant | 노이즈 큰 tenant 식별 |
| 검색 요청 rate | `rate(...search_latency_seconds_count[5m])` | Rate 보조 |
| Cluster-side latency | exporter 메트릭 (OpenSearch / ClickHouse 각각) | 본 서비스 vs cluster 의 시간 분리 |
| p99 stat | 현재 값 + threshold (1.5s / 3s) | SLO 위반 여부 즉시 인지 |

### Dashboard 3 — `Flink Correlation` (USE 의 Saturation + Errors)

streaming pipeline 의 자원 사용 / 병목.

| 패널 | 메트릭 | 의도 |
|---|---|---|
| records-in / records-out | `flink_taskmanager_job_task_numRecordsInPerSecond` | 통과량 |
| backpressure ratio | `...backPressuredTimeMsPerSecond / 1000` | Saturation — downstream 정체 |
| busy ratio | `...busyTimeMsPerSecond / 1000` | Utilization — task 부하 |
| checkpoint duration / size | `flink_jobmanager_job_lastCheckpointDuration` | exactly-once 비용 |
| failed checkpoints | `flink_jobmanager_job_numberOfFailedCheckpoints` | Errors — 즉시 alert |
| Kafka consumer lag | `kafka_consumergroup_lag` | 따라잡지 못하는지 |

### Dashboard 4 — `Alerts Overview` (도메인 지표)

SOC 운영자의 1차 화면.

| 패널 | 메트릭 | 의도 |
|---|---|---|
| 시간별 발생 (severity stack) | `increase(security_log_alert_fired_total[1h])` | 추이 |
| 24h 누적 (severity bargauge) | 같음, range 24h | 한 눈 요약 |
| Top 10 룰 | `topk(10, sum by (rule_id) (...))` | 노이즈 룰 식별 |
| False-positive ratio | `rate(...alert_closed_total{reason="false_positive"}) / rate(...alert_closed_total)` | 룰 품질 |
| 현재 OPEN 알람 수 | `sum(security_log_alert_open_count)` | backlog |
| tenant 별 발생 | group by tenant | 부하 분포 |

> Note: `security_log_alert_closed_total` / `security_log_alert_open_count` 는 본 ADR
> 시점에 placeholder. 후속 commit 에서 EvaluateAlertService / ListAlertsService 에 메트릭 추가
> 필요 — 룰 품질 지표가 SOC 운영의 핵심.

### 메트릭 명명 컨벤션

- prefix `security_log_` — 본 서비스가 발행하는 메트릭임을 명확히.
- counter 는 `_total` suffix.
- histogram 은 `_seconds` suffix + `_bucket` / `_count` / `_sum` 자동 생성.
- tag: `source`, `tenant`, `schema`, `rule_id`, `severity`, `type`, `reason`. 모두 lower case
  + 짧은 식별자.

cardinality 통제:

- `tenant` 가 수천 개로 증가하면 recording rule 로 압축 (e.g. `sls:ingest:rate5m` 에서 tenant
  drop).
- `rule_id` 도 수백 개 가능 — Top 10 만 dashboard 노출.

### Prometheus 측 구성

- `infrastructure/observability/prometheus/scrape-config.yaml` — 5개 source (service / Flink JM/TM /
  ClickHouse / OpenSearch / Kafka) scrape 설정. Recording rule + Alerting rule 예시 주석으로 포함.
- 운영 환경은 ServiceMonitor (Prometheus Operator) 로 변환해서 사용.

### Runbook 연동

- alert annotation 의 `runbook` 필드에 `docs/runbook/<alert-name>.md` 경로 명시.
- 첫 runbook: `docs/runbook/ingest-throughput-drop.md` — IngestRateDropped 알람 대응.

## 결과

- 새 운영자가 4개 대시보드 + 1개 runbook 만 봐도 시스템 상태 파악 가능.
- alert 가 떴을 때 *어느 패널을 봐야 하는지* 가 명확 — runbook 의 1번 항목이 항상 해당
  패널을 가리킴.
- 메트릭 이름 / 태그 컨벤션이 코드 (SecurityLogMetrics) + dashboard JSON + Prometheus rule 의
  3개 위치에서 동일하게 유지되도록 강제.

## 단점

- dashboard JSON 이 코드와 *분리* 되어 있어, 메트릭 이름이 바뀌면 grep 으로 두 곳 (코드 +
  JSON) 갱신 필요. 후속으로 Grafonnet / Jsonnet 으로 generate 검토.
- Flink / ClickHouse / OpenSearch exporter 의 메트릭 이름은 *외부 component 의 spec* 이라
  버전 업그레이드 시 깨질 수 있음. 회귀 검증 필요.
- false-positive ratio 같은 *도메인* 메트릭은 본 ADR 시점에 placeholder 로 남았음. 룰 품질
  추적이 운영의 핵심이라 후속 commit 우선순위.

## 다시 검토할 시점

- tenant 가 수백 개를 넘으면 cardinality 폭발. recording rule 로 tenant tag 를 drop 하거나
  per-tenant dashboard 로 분리.
- 운영자가 dashboard 를 *안 본다* 는 신호 (장애 대응 시 Slack 으로만 디버깅) 가 보이면
  dashboard 를 단순화 / 재구성.
- distributed tracing (OpenTelemetry) 도입 시점에 latency dashboard 와 trace 연동.

## 참고

- [Tom Wilkie — The RED Method](https://www.weave.works/blog/the-red-method-key-metrics-for-microservices-architecture/)
- [Brendan Gregg — The USE Method](https://www.brendangregg.com/usemethod.html)
- [Google SRE Book — Monitoring Distributed Systems (Four Golden Signals)](https://sre.google/sre-book/monitoring-distributed-systems/)
- [Apache Flink — System Metrics](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/ops/metrics/#system-metrics)
- [OpenSearch Prometheus Exporter](https://github.com/aiven/prometheus-exporter-plugin-for-opensearch)
- [ClickHouse — Prometheus endpoint](https://clickhouse.com/docs/en/operations/server-configuration-parameters/settings#prometheus)
