# Screenshots & demo captures

> **English first; 한국어는 아래에.** This directory holds the *recipes* for capturing the
> visual evidence of the demo flows. The images/casts themselves are **not committed yet** —
> capturing them requires the Docker stack to be up. Run the exact commands below on a machine
> with Docker, then drop the artifacts next to this README (filenames are pre-agreed so the
> main README can link them later).
>
> Nothing here is fabricated: every command is copy-pasteable and matches the scripts in
> [`scripts/`](../../scripts) and the compose files in
> [`infrastructure/docker/`](../../infrastructure/docker).

Rendered architecture diagrams (no Docker needed) already live in
[`docs/diagrams/`](../diagrams) — generated from the README Mermaid sources with
`@mermaid-js/mermaid-cli` (see [Re-rendering diagrams](#re-rendering-the-mermaid-diagrams)).

## Prerequisites

```bash
# from repo root
docker --version            # Docker Desktop / Engine running
which curl jq openssl       # integration-demo.sh deps (bash 4+, curl, jq, openssl)
# optional capture tools:
brew install asciinema agg  # terminal cast → animated GIF
brew install k6             # local k6 (otherwise run-load.sh falls back to docker run)
```

All commands below are run from the repository root.

---

## Capture 1 — integration demo (mock auth + mock notification-hub)

Brings up the mock `auth-service` (JWK Set) + mock `notification-hub` (consumes
`alerts.fired`) alongside the real Postgres / Kafka / OpenSearch / ClickHouse, then runs the
7-step demo: JWT mint → multi-tenant isolation → Sigma import → brute-force trigger → alert.

```bash
# 1. bring up the integration stack (mock auth + mock notification-hub + infra + app)
docker compose -f infrastructure/docker/docker-compose.integration.yml up -d

# 2. wait for the app to report UP (usually 30-60s)
until curl -sf http://localhost:8080/actuator/health | jq -e '.status=="UP"' >/dev/null; do
  echo "waiting for app..."; sleep 3
done

# 3. record the demo as an asciinema cast, then convert to GIF
asciinema rec --command './scripts/integration-demo.sh' docs/screenshots/integration-demo.cast
agg docs/screenshots/integration-demo.cast docs/screenshots/integration-demo.gif

# 4. capture the notification-hub receiving the same alert (proves external consume works)
docker compose -f infrastructure/docker/docker-compose.integration.yml \
  logs mock-notification-hub | tail -30 | tee docs/screenshots/notification-hub-consume.txt

# 5. tear down
docker compose -f infrastructure/docker/docker-compose.integration.yml down -v
```

Expected artifacts in this directory:

| file | what it shows |
|---|---|
| `integration-demo.gif` | the 7-step `integration-demo.sh` run end to end |
| `notification-hub-consume.txt` | `alerts.fired` payloads as the mock hub printed them |

What the cast proves (the script's own "검증 포인트"):
JWT verified via mock-auth JWK Set; acme data invisible to a globex token (4-layer isolation);
N Sigma rules imported; brute-force events drove `alerts.fired` → `alerts` INSERT; the same
alert was independently consumed by the mock notification-hub.

---

## Capture 2 — Sigma import → Flink → alert flow

The full pipeline against the standard (non-mock) compose stack: import 4 SigmaHQ rules as a
multi-document YAML, fire 5 brute-force events, watch the alert land.

> Diagram for this flow: [`docs/diagrams/sigma-alert-notification.svg`](../diagrams/sigma-alert-notification.svg).

```bash
# 1. infra + app
docker compose -f infrastructure/docker/docker-compose.yml up -d
docker compose -f infrastructure/docker/docker-compose.yml --profile app up -d

# 2. wait for health
until curl -sf http://localhost:8080/actuator/health | jq -e '.status=="UP"' >/dev/null; do sleep 3; done

# 3. seed a tenant + base rule, then record the Sigma demo
./scripts/seed_demo_data.sh
asciinema rec --command './scripts/import_sigma_demo.sh' docs/screenshots/sigma-flow.cast
agg docs/screenshots/sigma-flow.cast docs/screenshots/sigma-flow.gif

# 4. confirm the alert reached the alerts table (1-3s after Flink publishes)
curl -s 'http://localhost:8080/api/v1/alerts?tenantId=acme' | jq | tee docs/screenshots/alerts-after-sigma.json

# 5. (optional) Flink Web UI screenshot showing the running correlation job
open http://localhost:8081     # screenshot the Job overview → docs/screenshots/flink-job.png
```

Expected artifacts:

| file | what it shows |
|---|---|
| `sigma-flow.gif` | `import_sigma_demo.sh`: 4 rules imported + mappingNotes + 5 trigger events |
| `alerts-after-sigma.json` | the alert rows produced by the brute-force pattern |
| `flink-job.png` | Flink Web UI with the correlation job running (optional) |

---

## Capture 3 — k6 multi-tenant isolation load test

Runs the `multi-tenant-isolation` k6 scenario, which asserts the invariant
`tenant_leak_count == 0` (a globex token must never see acme data). See
[`docs/diagrams/multi-tenant-isolation.svg`](../diagrams/multi-tenant-isolation.svg) and
[ADR-0007](../adr/0007-multi-tenant-isolation.md).

```bash
# stack must be up (see Capture 2, steps 1-2). Then either run the whole suite:
./scripts/run-load.sh
# ...or just the isolation scenario (local k6):
k6 run \
  --summary-export=docs/screenshots/multi-tenant-isolation.summary.json \
  load/k6/scenarios/multi-tenant-isolation.js | tee docs/screenshots/multi-tenant-isolation.txt

# no local k6? use the dockerized runner (same image run-load.sh falls back to):
docker run --rm -i -v "$PWD/load/k6:/scripts:ro" \
  -e BASE_URL=http://host.docker.internal:8080 \
  grafana/k6:0.50.0 run /scripts/scenarios/multi-tenant-isolation.js
```

Expected artifacts:

| file | what it shows |
|---|---|
| `multi-tenant-isolation.txt` | k6 console output with the `tenant_leak_count` threshold ✓ |
| `multi-tenant-isolation.summary.json` | machine-readable k6 summary (thresholds, metrics) |

The key line to capture is the threshold check: `✓ tenant_leak_count` with `count==0` passing —
proof the isolation invariant held under concurrent load.

---

## Re-rendering the Mermaid diagrams

The SVGs in [`docs/diagrams/`](../diagrams) are generated (not Docker-dependent — just Node):

```bash
cat > /tmp/puppeteer.json <<'EOF'
{ "args": ["--no-sandbox", "--disable-setuid-sandbox"] }
EOF
for d in processing-flow sigma-alert-notification multi-tenant-isolation; do
  npx --yes @mermaid-js/mermaid-cli@11 -p /tmp/puppeteer.json \
    -i "docs/diagrams/$d.mmd" -o "docs/diagrams/$d.svg" -b transparent
done
```

The `.mmd` sources mirror the Mermaid blocks in the top-level [`README.md`](../../README.md);
keep them in sync when the README diagrams change.

---

## 한국어 안내

이 디렉토리는 데모 흐름의 **시각 자료 캡처 방법(레시피)** 만 담는다. 실제 GIF/cast 는
Docker 스택이 떠 있어야 캡처 가능하므로 **아직 커밋하지 않았다**. Docker 가 있는 머신에서
위 명령을 그대로 실행해 산출물을 이 디렉토리에 떨구면 된다(파일명은 위 표에 고정).

- **Capture 1** — `docker-compose.integration.yml` (mock auth + mock notification-hub) +
  `scripts/integration-demo.sh`: JWT 검증 / 멀티테넌트 격리 / Sigma import / alert →
  notification-hub consume 까지 한 번에. asciinema 로 녹화 후 `agg` 로 GIF 변환.
- **Capture 2** — `scripts/import_sigma_demo.sh`: Sigma 4건 import → 트리거 이벤트 →
  Flink → `alerts.fired` → alerts 테이블 INSERT. Flink Web UI(:8081) 스크린샷은 선택.
- **Capture 3** — k6 `multi-tenant-isolation` 시나리오: `tenant_leak_count == 0` invariant
  (globex 토큰이 acme 데이터를 한 건도 못 봄) 가 부하 중에도 유지되는지 검증.

아키텍처 다이어그램(Docker 불필요)은 [`docs/diagrams/`](../diagrams) 에 SVG 로 렌더링돼 있다.
README 의 Mermaid 블록을 `@mermaid-js/mermaid-cli` 로 생성한 것이며, 위 [재생성 명령](#re-rendering-the-mermaid-diagrams)
으로 다시 만들 수 있다.
