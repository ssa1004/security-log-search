# security-log-search Helm chart

SIEM 형태의 보안 로그 수집 / 검색 / 알람 플랫폼을 Kubernetes 에 배포하는 Helm chart 입니다.

본 chart 는 Spring 기반 REST API (ingest / search / alerts / sigma / admin) 만 다룹니다.
Apache Flink correlation job 은 별도 클러스터 (Flink Kubernetes Operator 권장) 에 배포하는
것을 가정합니다 (`templates/flink-streaming.yaml` 은 환경 변수 stub 만 노출).

## 사전 조건

- Kubernetes 1.27+ / Helm 3.x
- 외부 의존:
  - PostgreSQL (control plane: tenants / alert_rules / alerts / audit_entries)
  - Kafka (events.normalized / alerts.fired)
  - OpenSearch (full-text 검색)
  - ClickHouse (대용량 집계 / 시계열)
  - Redis (idempotency-key cache + rate limiter)
  - OIDC issuer (auth-service 등) — JWT 검증
- Prometheus Operator (선택, ServiceMonitor 사용 시)
- cert-manager + ingress-nginx (선택, TLS ingress 사용 시)

OpenSearch / ClickHouse 는 별도 클러스터를 가정합니다 (chart 에서 운영하지 않고 endpoint
만 주입). PostgreSQL / Kafka / Redis 도 동일.

## 빠른 사용

```bash
# dev
helm install slq . --namespace security-log-search --create-namespace \
  --values values-dev.yaml

# staging
helm install slq . --namespace security-log-search --create-namespace \
  --values values-staging.yaml

# prod
helm install slq . --namespace security-log-search --create-namespace \
  --values values-prod.yaml
```

업그레이드:

```bash
helm upgrade slq . --namespace security-log-search --values values-prod.yaml
```

## values 주요 키

| 키 | 기본값 | 설명 |
|---|---|---|
| `image.repository` | `ghcr.io/ssa1004/security-log-search` | 컨테이너 이미지 |
| `image.tag` | `main` | 이미지 태그 (운영은 commit SHA 권장) |
| `replicaCount` | 3 | replica 수 (HPA 활성 시 minReplicas 가 우선) |
| `resources.*` | cpu 0.5/2 mem 1Gi/2Gi | requests / limits |
| `autoscaling.enabled` | false | values-prod 에서 true. cpu 70% / mem 75% |
| `autoscaling.minReplicas` / `maxReplicas` | 3 / 12 | prod 는 2 / 10 |
| `podDisruptionBudget.enabled` | true | dev 는 false |
| `service.type` / `port` | ClusterIP / 80 | |
| `ingress.enabled` | false | prod 만 true |
| `ingress.hosts[].paths` | SIEM path 6종 | events / search / alerts / sigma-rules / audit / stats |
| `ingress.admin.enabled` | false | prod 는 true. 별도 host + IP allowlist |
| `ingress.tls` | `[]` | prod 는 cert-manager 발급 secret |
| `gracefulShutdown.preStopSleepSeconds` | 15 | endpoint propagation 지연 보전 |
| `gracefulShutdown.terminationGracePeriodSeconds` | 60 | 강제 SIGKILL 전 시간 |
| `gracefulShutdown.springTimeoutSeconds` | 30 | Spring 의 in-flight drain 대기 |
| `serviceAccount.create` | true | |
| `serviceAccount.rbac.create` | true | configmap / secret read role |
| `secrets.existingSecret` | `""` | 외부 secret 참조 시 이름 지정 |
| `monitoring.serviceMonitor.enabled` | true | dev 는 false |
| `networkPolicy.enabled` | true | dev 는 false. prod 기본 활성 (ISMS-P 권고) |
| `config.opensearch.host` | 클러스터 내 service | 외부 클러스터면 endpoint 직접 |
| `config.clickhouse.url` | jdbc:clickhouse://... | 외부 클러스터면 JDBC URL 직접 |
| `config.kafka.bootstrap` | kafka.kafka.svc... | |
| `config.postgres.*` | postgres.postgres.svc... | host / port / name / user |
| `config.redis.*` | redis.redis.svc... | host / port / database |
| `config.oauth.issuerUri` | https://auth.example.com | OIDC issuer (auth-service 등) |
| `streaming.parallelism` | 2 | Flink job parallelism (별도 chart) |

## 환경별 차이

| 항목 | dev | staging | prod |
|---|---|---|---|
| replicaCount | 1 | 2 | 3 |
| HPA | off | off | on (2-10) |
| PDB | off | on (1) | on (2) |
| Ingress | off | off | on (TLS + admin 분리) |
| NetworkPolicy | off | on | on |
| ServiceMonitor | off | on | on |
| Spring profile | dev | prod | prod |

## SIEM 특성

### 멀티테넌트 4 layer 격리

본 chart 는 인프라만 다루고, 멀티테넌트 격리는 application layer 에서 강제합니다
(README → "멀티테넌트 격리" 절). chart 가 보장하는 것은:

- Pod 별 ServiceAccount 분리 + RBAC 최소 권한
- NetworkPolicy 로 의존 외 외부 통신 차단

### admin endpoint 분리

`/api/v1/admin/*` (인덱스 / 룰 관리) 는 별도 host (`security-log-admin.example.com`) +
IP allowlist annotation (`nginx.ingress.kubernetes.io/whitelist-source-range`) 으로
운영자 전용 노출. prod values 에서 활성.

### 외부 cluster 가정

OpenSearch / ClickHouse / PostgreSQL / Kafka / Redis 는 본 chart 에서 운영하지 않습니다.
운영 환경에서는 각자의 operator (예: opensearch-operator, clickhouse-operator) 또는
managed service 의 endpoint 만 `config.*` 에 주입.

### Flink job

`templates/flink-streaming.yaml` 은 환경 변수만 노출하는 ConfigMap stub 입니다. 실제
job 운영은 [Flink Kubernetes Operator](https://github.com/apache/flink-kubernetes-operator)
의 `FlinkDeployment` / `FlinkSessionJob` CR 로 별도 관리.

## Secret 운영

`templates/secret.yaml` 은 dev / staging 편의용 placeholder 입니다 (값이 git 에 들어감).
prod 에서는 다음 중 하나로 대체:

1. **SealedSecret** — `bitnami/sealed-secrets` 로 암호화된 manifest 를 git 에 커밋
2. **ExternalSecret** — Vault / AWS Secrets Manager / GCP Secret Manager 와 동기화
3. **CSI Secret Store** — pod mount 시점에 secret backend 에서 직접 fetch

외부 secret 을 미리 만들어 두고 `secrets.existingSecret: <name>` 으로 지정하면
chart 가 자체 secret 을 만들지 않고 그 이름만 참조합니다. 필요한 key:

- `db-password`
- `clickhouse-password`
- `redis-password` (선택, Redis 가 password 를 요구하는 경우)

## 검증

```bash
helm lint .
helm lint . --values values-prod.yaml

helm template release . --values values-prod.yaml | kubectl apply --dry-run=client -f -
```

## 의존 chart

본 chart 는 의존성이 없습니다 (umbrella chart 가 아님). 외부 cluster 를 가정.
PostgreSQL / Kafka / OpenSearch / ClickHouse / Redis 가 같은 클러스터에 있다면
각자의 공식 chart (예: `bitnami/postgresql`, `bitnami/kafka`) 를 별도로 설치.

## 추가 참고

- `infrastructure/argocd/applicationset.yaml` — dev / staging / prod 3개 Application
  자동 생성
- `infrastructure/k8s/` — Helm 도입 전 raw manifest (참고용)
- ADR-0010 (ISMS-P 통제 매핑) — NetworkPolicy / RBAC / Audit 의 통제 근거
