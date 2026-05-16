# Load test (k6)

security-log-search 의 5 가지 SIEM 부하 시나리오. 단순 RPS 가 아니라 *SIEM 특유의 비용*
을 본다 — OpenSearch ingestion throughput, Lucene full-text 비용, terms aggregation
(facet) 비용, multi-tenant 격리 invariant, Sigma 룰 적용 후 Flink streaming end-to-end
latency.

## 디렉토리

```
load/
├── README.md
└── k6/
    ├── lib/
    │   ├── auth.js          # K6_TOKEN / tenant 별 토큰 + unsigned JWT 헬퍼
    │   └── config.js        # BASE_URL, tenant / source / severity pool, ECS payload 빌더
    └── scenarios/
        ├── log-ingest.js              # POST /events 2000 req/s — OpenSearch ingestion
        ├── full-text-search.js        # POST /search 100 req/s — Lucene query 비용
        ├── facet-aggregation.js       # POST /search w/ facets 50 req/s — aggregation 비용
        ├── multi-tenant-isolation.js  # acme publish → globex search → 0건 invariant
        └── alert-rule-eval.js         # Sigma import → trigger → alert.fired latency
```

## 사전 준비

세 가지 방법 중 하나.

### A. brew 로 로컬 설치

```bash
brew install k6
k6 version
```

### B. docker 직접 실행

```bash
docker run --rm -i grafana/k6 run - < load/k6/scenarios/log-ingest.js
```

### C. docker-compose 통합 환경

`infrastructure/docker/docker-compose.yml` 로 PostgreSQL + Kafka + OpenSearch +
ClickHouse + Flink 를 띄운 뒤 앱을 같이 (또는 별도 bootRun) 띄우고 k6 를 외부에서 호출.

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d
docker compose -f infrastructure/docker/docker-compose.yml --profile app up -d
./scripts/seed_demo_data.sh                 # tenant 'globex' onboarding + 기본 룰
./scripts/run-load.sh                       # 5 시나리오 일괄
```

기본 endpoint 는 `http://localhost:8080`. dev profile 에서는 SecurityConfig 가
`anyRequest().permitAll()` 이라 토큰 없이도 모든 시나리오가 동작한다. prod 게이트가
켜진 환경에서는 `K6_TOKEN` / `K6_TOKEN_ACME` / `K6_TOKEN_GLOBEX` 를 주입해야 한다.

## 시나리오별 실행

### 1) log-ingest — OpenSearch ingestion throughput

`POST /api/v1/events` 가 raw event 를 받아 EventNormalizer 로 ECS / OCSF 변환 후
Kafka (`events.normalized`) 로 publish, downstream consumer 가 OpenSearch bulk +
ClickHouse 로 적재. 본 시나리오는 *수집 진입점* 의 latency 만 본다 — controller 가
normalize + Postgres `idempotency_keys` lookup + Kafka producer.send().get() 까지 한
호출에 처리하므로 그 비용 전체가 측정 대상.

```bash
k6 run load/k6/scenarios/log-ingest.js
```

| metric | 기준 |
|---|---|
| `http_req_duration{name:log-ingest}` p95 / p99 | < 100ms / 300ms |
| `http_req_failed` | < 1% |
| `ingest_lag_ms` p95 (보조 — server-side waiting) | < 100ms |

### 2) full-text-search — Lucene query 비용

`POST /api/v1/search` 의 query 만 채운 경량 body. facet 이 없어 OpenSearch 비용은
query parser + bm25 scoring 만 들어간다. tenant alias (`events-{tenant}-read`) 로
좁혀 검색.

```bash
k6 run load/k6/scenarios/full-text-search.js
```

| metric | 기준 |
|---|---|
| `http_req_duration{name:full-text-search}` p95 / p99 | < 300ms / 800ms |
| `http_req_failed` | < 1% |
| `search_p99` p99 (보조 — TTFB tail) | < 800ms |

### 3) facet-aggregation — aggregation 비용

`POST /api/v1/search` 의 풀 body — query + filters + facets 3개 (terms aggregation).
full-text-search 의 p95 와 본 시나리오의 p95 차이가 facet aggregation 의 *순수* 비용에
가깝다 (네트워크 / Kafka / Postgres 변수는 양쪽 동일).

```bash
k6 run load/k6/scenarios/facet-aggregation.js
```

| metric | 기준 |
|---|---|
| `http_req_duration{name:facet-aggregation}` p95 / p99 | < 500ms / 1500ms |
| `http_req_failed` | < 1% |
| `facet_compute_ms` p95 (보조 — server-side waiting) | < 400ms |

### 4) multi-tenant-isolation — 보안 회귀 가드

ADR-0007 의 4-layer 격리 (controller / use case / OpenSearch alias / ClickHouse
partition) 의 회귀를 invariant 로 차단. acme tenant 로 publish 한 표식 event 가
globex tenant 의 search 에서 한 건이라도 보이면 즉시 실패.

```bash
k6 run load/k6/scenarios/multi-tenant-isolation.js
```

| metric | 의미 / 기준 |
|---|---|
| `tenant_leak_count` count | 0 (invariant — leak 발생 즉시 실패) |
| `tenant_sanity_failure` count | 0 (publish 가 실패해 시나리오 자체가 무효) |
| `http_req_failed` | < 5% |

토큰이 dev fallback 인 경우 body 의 `tenantId` 필드만으로 격리를 검증 (controller →
OpenSearch alias 라우팅). prod 환경에서는 `K6_TOKEN_ACME` / `K6_TOKEN_GLOBEX` 가 각각
다른 `tenant_id` claim 을 담은 토큰이어야 invariant 가 의미를 갖는다.

### 5) alert-rule-eval — Sigma → Flink → alert.fired latency

Sigma YAML 1건 (5분 안 같은 IP 5회 logon failure) 을 import 한 뒤, 한 iteration 마다
트리거 이벤트 5건을 발사 → `GET /api/v1/alerts` polling 으로 alert 가 나타날 때까지의
시간 측정. Flink job 의 broadcast state 가 새 룰을 받아 평가에 반영하는 경로 + Kafka
`alerts.fired` → Spring consumer INSERT 까지의 end-to-end.

```bash
k6 run load/k6/scenarios/alert-rule-eval.js
```

| metric | 의미 / 기준 |
|---|---|
| `alert_fired_latency_ms` p95 | < 5000ms (streaming end-to-end) |
| `alert_fired_count` count | > 0 (한 건도 안 fired 면 setup 자체 실패) |
| `sigma_import_failure` count | 0 (setup 단계 실패는 모든 후속 측정 무효) |
| `alert_not_fired_within_deadline` count | 분포 관측 — deadline 안에 안 잡힌 케이스 |
| `http_req_failed` | < 5% |

## 일괄 실행

5 시나리오를 순차 실행 (각 결과를 `build/k6-reports/<scenario>.json` 으로 떨군다):

```bash
./scripts/run-load.sh                        # 기본 BASE_URL=http://localhost:8080
BASE_URL=http://staging:8080 ./scripts/run-load.sh
```

## 환경변수

| key | 기본 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | HTTP base — prod 또는 staging 으로 덮어쓰기 |
| `K6_TOKEN` | (빈 값) | 단일 tenant 시나리오의 Bearer 토큰 |
| `K6_TOKEN_ACME` | (빈 값) | multi-tenant-isolation 의 acme 토큰 |
| `K6_TOKEN_GLOBEX` | (빈 값) | multi-tenant-isolation 의 globex 토큰 |
| `K6_TENANTS` | `acme,globex` | tenant pool CSV |
| `K6_QUERIES` | 10개 한/영 + Lucene 혼합 키워드 | round-robin query CSV |
| `K6_ALERT_TENANT` | `acme` | alert-rule-eval 에서 Sigma 룰 / 이벤트를 보낼 tenant |

## SIEM 특유 측정 항목

REST 부하 측정의 표준 `http_req_duration` / `http_req_failed` 외에, SIEM service 특유의
비용을 분리해 보기 위해 시나리오 안에서 다음 metric 들을 직접 정의한다.

| 커스텀 metric | 시나리오 | 의미 |
|---|---|---|
| `ingest_lag_ms` | log-ingest | server-side TTFB — normalize + idempotency + Kafka producer ack 까지 |
| `search_p99` | full-text-search | 검색 경로의 p99 tail latency 신호 (보조) |
| `facet_compute_ms` | facet-aggregation | facet 포함 query 의 server-side waiting. full-text 와의 차이가 aggregation 단독 비용 |
| `tenant_leak_count` | multi-tenant-isolation | acme 데이터가 globex 검색에 노출된 건수. **invariant: 0** |
| `tenant_sanity_failure` | multi-tenant-isolation | acme 자체 sanity search 가 실패한 건수 — publish 경로 자체가 깨졌다는 신호 |
| `alert_fired_latency_ms` | alert-rule-eval | 트리거 마지막 publish → alert.firedAt 까지의 end-to-end |
| `alert_fired_count` | alert-rule-eval | fired alert 누적 — 0 이면 시나리오 무효 |
| `sigma_import_failure` | alert-rule-eval | setup 단계 Sigma import 실패 카운터 |
| `alert_not_fired_within_deadline` | alert-rule-eval | deadline 안에 alert 가 안 잡힌 case — pipeline lag 신호 |

## k6 표준 metric 해석

| metric | 의미 |
|---|---|
| `vus` / `vus_max` | 현재 / 최대 VU |
| `iter_duration` | 한 default 함수 실행 시간 — sleep 포함 |
| `http_req_duration` | HTTP 응답 소요 — connect / TLS / waiting 합 |
| `http_req_waiting` | TTFB (server-side latency 의 근사) |
| `http_req_failed` | non-2xx 비율 |
| `data_received` / `data_sent` | byte 카운터 |

### p95 / p99 보는 법

- **p95** 는 변동성 신호 (95 백분위) — 일상 SLO 의 기준.
- **p99** 는 꼬리 신호 — GC, OpenSearch query cache miss, Kafka producer queue 고갈 등 드문
  이벤트.
- p95 → p99 격차가 크면 운영 환경의 reliability tail 이 두꺼운 것 — Resilience4j circuit
  breaker / OpenSearch query cache size 부터 본다.

### 시나리오별 부하 모델

| 시나리오 | executor | 모델 |
|---|---|---|
| log-ingest | constant-arrival-rate | 2000 req/s, 60s |
| full-text-search | constant-arrival-rate | 100 req/s, 60s |
| facet-aggregation | constant-arrival-rate | 50 req/s, 60s |
| multi-tenant-isolation | ramping-vus | 0 → 3 VU, 45s |
| alert-rule-eval | ramping-vus | 0 → 2 VU, 75s |

`constant-arrival-rate` 는 throughput 기준 (read / write 일반), `ramping-vus` 는 한
iteration 안에 setup → publish → polling 의 누적 상태가 중요한 (격리 / alert eval) 쪽에
쓴다.

## 결과 plot

각 시나리오를 `--out json=build/k6-reports/<name>.json` 으로 떨궈서 dashboard 에 올릴 수
있다.

## Prometheus remote-write 연동 (commerce-ops 통합 대시보드)

5 시나리오 결과를 `commerce-ops` 의 Prometheus 로 흘려보내 한 Grafana 대시보드에서
client load + SIEM 의 server actuator metric 을 같이 보고 싶을 때:

```bash
docker compose -f /path/to/commerce-ops/infra/docker-compose.yml up -d prometheus grafana

export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
./scripts/run-load.sh
```

`run-load.sh` 가 각 시나리오에 `service=security-log-search` / `scenario=<name>` tag 를
자동 부여한다. Grafana → **Portfolio Load (k6 + actuator)** 대시보드 (uid
`portfolio-load`) 에서 service 변수를 `security-log-search` 로 선택. 12번 패널
"tenant_leak_count" 가 빨간색으로 잡히면 multi-tenant-isolation 의 격리 invariant 가
깨진 신호 (5분 윈도우 leak/5m > 0). 필요 k6 버전 **0.42+** (experimental-prometheus-rw output).

## 더 나아가려면

더 큰 부하는 k6 cloud / k6 distributed mode 가 필요 — 본 시나리오는 single-node 기준
이라 VU 수십 ~ 수백 선에서 운용한다. log-ingest 의 2000 req/s 도 OpenSearch / Kafka /
Postgres 셋의 single-node 한도 안에서 평소 정상치를 다소 상회하는 정도라, distributed
부하가 필요하면 OpenSearch shard 수 + Kafka partition 수도 같이 늘려야 의미가 있다.
