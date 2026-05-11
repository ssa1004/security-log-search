# Runbook — ingest rate 급감 진단

알람: `IngestRateDropped` (`sum(rate(security_log_ingest_total[5m]))` 가 1시간 전 대비
50% 이하로 하락, 5분 지속).

## 1. 영향 범위 확인

먼저 어디까지 영향이 있는지부터.

- Grafana — `Security Log Search → Ingest Throughput` 대시보드 열기.
- "Ingest rate — source 별" 패널에서 어떤 source 가 떨어졌는지 확인:
  - 단일 source 만 떨어졌는지 / 모든 source 가 떨어졌는지.
  - 단일 tenant 만 떨어졌는지 / 모든 tenant 가 떨어졌는지.
- "Ingest 실패 비율" 패널에서 실패율이 동시에 올라갔는지.

판정:

- 모든 source × 모든 tenant 동시 하락 → **본 서비스 / Kafka / DB 측 장애** (4번 항목으로).
- 단일 source 만 → **수집 agent 측 문제** (3번 항목으로).
- 단일 tenant 만 → **그 tenant 의 수집 agent 또는 인증 만료** (5번 항목으로).

## 2. 5분 우회 (담을 그릇이 없으면 흘려보내기 전에)

ingest rate 가 비정상적으로 낮으면 일반적으로 데이터 유실 위험이 있다. 가능하면 client
측 buffer 를 늘려 본 서비스 회복까지 기다리게 한다.

- Fluent Bit / Filebeat 사용 시 — `Storage.type=filesystem` + `Mem_Buf_Limit` 확인.
- 본 서비스 측 — Kafka 가 살아있다면 client 가 직접 Kafka 로 producer 하는 우회 경로
  유효 (단 schema 검증은 우회됨, 후속 backfill 필요).

## 3. 단일 source 만 하락 — 수집 agent 점검

- 해당 source 의 수집 agent 로그:
  - CloudTrail → CloudWatch Logs 의 `/aws/eventbridge/...` group.
  - K8s audit → kube-apiserver 의 `--audit-log-path` (보통 `/var/log/kubernetes/audit/`)
    또는 audit webhook backend 측.
  - Filebeat / Fluent Bit → `journalctl -u filebeat` / `kubectl logs -n logging fluent-bit-*`.
- 본 서비스 측 정규화 실패 메트릭:
  - `security_log_normalize_failures_total{source="..."}` 가 동시에 증가했는지.
  - 증가했다면 raw payload 가 schema 변경된 가능성 (예: K8s audit v1 → v2). 매퍼 갱신
    필요 (ADR-0014).
- 본 서비스 로그:
  ```
  kubectl logs -n security deploy/security-log-search --tail=500 | grep -E "ERROR|UnsupportedSchema|IllegalArgument"
  ```

## 4. 모든 source × 모든 tenant 하락 — 본 서비스 / Kafka / DB 점검

순서대로 확인:

1. **본 서비스 health**
   - `kubectl get pod -n security -l app=security-log-search` — Running 인지.
   - `kubectl logs -n security deploy/security-log-search --tail=200`
   - `/actuator/health` — `status: UP` 확인.

2. **Kafka producer 측 메트릭**
   - Grafana → "Kafka producer rate" 패널 — 본 서비스가 Kafka 로 producer 하는 rate 가
     떨어졌는지.
   - `kafka_producer_record_send_total` 이 0 에 가까우면 → broker 연결 / 권한 문제.
   - `kafka-topics --bootstrap-server ... --describe --topic events.normalized` — partition
     leader / ISR 정상 확인.

3. **Postgres (control plane) 측**
   - tenant lookup / idempotency check 가 매 ingest 마다 일어남. Postgres 가 느려지면
     ingest throughput 도 같이 떨어진다.
   - `kubectl exec -it postgres-0 -- psql -c 'select count(*) from idempotency_keys;'` —
     idempotency 테이블 사이즈 (5분 이상 오래된 레코드는 cleanup 필요. ADR-0011).
   - `pg_stat_activity` 에 idle in transaction 누적 여부.

4. **JVM heap / GC**
   - `/actuator/metrics/jvm.gc.pause` — recent GC pause 가 길지 않은지.
   - heap 사용률 90% 이상이면 OOM 직전. heap dump 받고 재시작 검토.

5. **CPU / network**
   - `kubectl top pod -n security` — CPU throttling 여부.
   - HPA 가 작동 중인지 (`kubectl get hpa -n security`).

## 5. 단일 tenant 만 하락

- `tenants` 테이블에서 해당 tenant 의 `active` 플래그 확인.
  ```
  select id, active, updated_at from tenants where id = '<tenant>';
  ```
- 비활성 (`active=false`) 으로 바뀌어 있으면 — 의도된 차단인지 확인 (영업 / 결제 / 정책).
- 활성인데 ingest 가 0 → 수집 agent 의 인증 토큰 / API key 만료 가능. 해당 tenant 측
  contact 로 확인.

## 6. 회복 후

- ingest rate 가 정상 수치로 복귀되면 알람 자동 resolve.
- Postmortem 항목으로 기록:
  - 원인 / 영향 시간 / 영향 받은 데이터 양 (ingest 가 멈춘 시간 동안 누락된 event 추정).
  - upstream 의 buffer 가 회복 시 backfill 했는지 (Filebeat 등은 디스크 buffer 가 있어
    회복 시 자동 catchup, agent 가 메모리 buffer 만 쓰면 유실).
  - 재발 방지 — 수집 agent 측 buffer 늘리기 / 본 서비스 캐파 plan 갱신 / 알람 임계값 조정.

## 관련 문서

- ADR-0009 — Backpressure 정책
- ADR-0011 — Audit log append-only
- ADR-0014 — source 매퍼 분리
- 대시보드: `infrastructure/observability/grafana/dashboards/ingest-throughput.json`
- Prometheus alert rule: `infrastructure/observability/prometheus/scrape-config.yaml` 의 주석 절
