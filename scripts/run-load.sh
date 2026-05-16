#!/usr/bin/env bash
# k6 부하 시나리오 5종 일괄 실행.
#
# 단계:
#   1) 본 앱 healthcheck (없으면 compose 를 띄우라고 안내)
#   2) 추가 tenant onboard (globex) 사전 보장 — multi-tenant-isolation 시나리오의 전제
#   3) k6 실행 경로 결정 — 우선 로컬 k6, 없으면 docker run
#   4) log-ingest → full-text-search → facet-aggregation → multi-tenant-isolation
#      → alert-rule-eval 순서로 시나리오 실행
#   5) 각 결과는 build/k6-reports/{scenario}.json 에 떨군다
#
# 환경 변수:
#   BASE_URL — 시나리오의 endpoint base. 기본은 docker-compose 의 8080
#   K6_TOKEN — JWT 게이트가 켜진 환경에서만 의미. dev / local 이면 빈 값
#   K6_TOKEN_ACME / K6_TOKEN_GLOBEX — multi-tenant-isolation 의 tenant 별 토큰
#   K6_ALERT_TENANT — alert-rule-eval 의 tenant (기본 acme)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SCENARIO_DIR="${ROOT_DIR}/load/k6/scenarios"
REPORT_DIR="${ROOT_DIR}/build/k6-reports"
mkdir -p "$REPORT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
K6_TOKEN="${K6_TOKEN:-}"
K6_TOKEN_ACME="${K6_TOKEN_ACME:-}"
K6_TOKEN_GLOBEX="${K6_TOKEN_GLOBEX:-}"
K6_ALERT_TENANT="${K6_ALERT_TENANT:-acme}"
WAIT_SECONDS="${WAIT_SECONDS:-60}"

# k6 → Prometheus remote-write (optional). commerce-ops Prometheus 가 떠 있을 때
# `K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write` 를 export.
K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-}"
K6_PROMETHEUS_RW_TREND_STATS="${K6_PROMETHEUS_RW_TREND_STATS:-p(95),p(99),min,max,avg}"
K6_PROMETHEUS_RW_PUSH_INTERVAL="${K6_PROMETHEUS_RW_PUSH_INTERVAL:-5s}"
SERVICE_TAG="security-log-search"

echo "==> base url: $BASE_URL"
if [[ -n "$K6_PROMETHEUS_RW_SERVER_URL" ]]; then
    echo "==> k6 → Prometheus RW: $K6_PROMETHEUS_RW_SERVER_URL (service=$SERVICE_TAG)"
fi

# 1) healthcheck — actuator/health 가 UP 이 될 때까지 짧게 polling.
echo
echo "==> health 대기 ($BASE_URL/actuator/health)"
DEADLINE=$(( $(date +%s) + WAIT_SECONDS ))
until curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1; do
    if (( $(date +%s) >= DEADLINE )); then
        cat <<EOF
ERROR: $BASE_URL 가 $WAIT_SECONDS 초 안에 응답하지 않았습니다.

먼저 본 앱을 띄우세요:

  1) 단독 bootRun:
       ./gradlew :security-bootstrap:bootRun

  2) docker-compose 통합 환경:
       docker compose -f infrastructure/docker/docker-compose.yml up -d
       docker compose -f infrastructure/docker/docker-compose.yml --profile app up -d

또는 BASE_URL 를 staging 등으로 덮어쓰세요 (예: BASE_URL=http://staging:8080).
EOF
        exit 1
    fi
    sleep 2
done
echo "    UP"

# 2) tenant 'globex' 보장 — POST /api/v1/tenants. 이미 있으면 4xx 가 떨어지는데 무시.
#    multi-tenant-isolation 시나리오가 globex 로 검색하기 때문에 미리 onboard 해 두지
#    않으면 alias 가 없어 4xx 가 나고 invariant 측정이 무효가 된다.
echo
echo "==> tenant 'globex' onboard 보장"
ONBOARD_STATUS=$(curl -s -o /dev/null -w '%{http_code}' \
    -X POST "$BASE_URL/api/v1/tenants" \
    -H 'Content-Type: application/json' \
    -d '{
        "tenantId": "globex",
        "displayName": "Globex Corporation",
        "retention": "P730D",
        "hotRetention": "P14D",
        "piiPolicy": "STRICT"
    }' || true)
case "$ONBOARD_STATUS" in
    200|201) echo "    onboard OK ($ONBOARD_STATUS)";;
    409|400) echo "    이미 존재 ($ONBOARD_STATUS) — 무시";;
    *)       echo "    경고: 예상 외 status=$ONBOARD_STATUS — multi-tenant-isolation 결과 의심";;
esac

# 3) k6 실행 경로 결정.
if command -v k6 >/dev/null 2>&1; then
    K6_EXEC=("k6")
    echo
    echo "==> 로컬 k6 사용 ($(k6 version | head -1))"
elif command -v docker >/dev/null 2>&1; then
    # docker 안에서 호스트의 localhost 를 보려면 host.docker.internal.
    if [[ "$BASE_URL" == *"localhost"* || "$BASE_URL" == *"127.0.0.1"* ]]; then
        BASE_URL_DOCKER="${BASE_URL//localhost/host.docker.internal}"
        BASE_URL_DOCKER="${BASE_URL_DOCKER//127.0.0.1/host.docker.internal}"
    else
        BASE_URL_DOCKER="$BASE_URL"
    fi
    K6_RW_URL_DOCKER="${K6_PROMETHEUS_RW_SERVER_URL//localhost/host.docker.internal}"
    K6_RW_URL_DOCKER="${K6_RW_URL_DOCKER//127.0.0.1/host.docker.internal}"
    K6_EXEC=(docker run --rm -i \
        -v "${ROOT_DIR}/load/k6:/scripts:ro" \
        -e "BASE_URL=${BASE_URL_DOCKER}" \
        -e "K6_TOKEN=${K6_TOKEN}" \
        -e "K6_TOKEN_ACME=${K6_TOKEN_ACME}" \
        -e "K6_TOKEN_GLOBEX=${K6_TOKEN_GLOBEX}" \
        -e "K6_ALERT_TENANT=${K6_ALERT_TENANT}" \
        -e "K6_PROMETHEUS_RW_SERVER_URL=${K6_RW_URL_DOCKER}" \
        -e "K6_PROMETHEUS_RW_TREND_STATS=${K6_PROMETHEUS_RW_TREND_STATS}" \
        -e "K6_PROMETHEUS_RW_PUSH_INTERVAL=${K6_PROMETHEUS_RW_PUSH_INTERVAL}" \
        grafana/k6:0.50.0)
    SCRIPT_PREFIX="/scripts/scenarios"
    echo
    echo "==> docker run grafana/k6 사용"
else
    echo "ERROR: k6 도 docker 도 없습니다. brew install k6 또는 docker 설치 후 다시 시도하세요." >&2
    exit 1
fi

# 4) 시나리오 실행 — 한 단계 실패해도 다음 단계는 진행 (threshold 위반도 다음 시나리오의
#    측정 자체에는 영향 없음).
run_scenario() {
    local name="$1"
    local file="$2"

    echo
    echo "==> [$name] start ($(date +%H:%M:%S))"
    local out="${REPORT_DIR}/${name}.json"
    local rc=0

    local rw_opts=()
    if [[ -n "$K6_PROMETHEUS_RW_SERVER_URL" ]]; then
        rw_opts=(-o "experimental-prometheus-rw" \
                 --tag "service=${SERVICE_TAG}" \
                 --tag "scenario=${name}")
    fi

    if [[ "${K6_EXEC[0]}" == "k6" ]]; then
        export BASE_URL K6_TOKEN K6_TOKEN_ACME K6_TOKEN_GLOBEX K6_ALERT_TENANT \
               K6_PROMETHEUS_RW_SERVER_URL K6_PROMETHEUS_RW_TREND_STATS K6_PROMETHEUS_RW_PUSH_INTERVAL
        set +e
        "${K6_EXEC[@]}" run "${rw_opts[@]}" --summary-export="$out" "$file"
        rc=$?
        set -e
    else
        local docker_file="${SCRIPT_PREFIX}/$(basename "$file")"
        local docker_out="/scripts/${name}.summary.json"
        set +e
        "${K6_EXEC[@]}" run "${rw_opts[@]}" --summary-export="$docker_out" "$docker_file"
        rc=$?
        set -e
        if [[ -f "${ROOT_DIR}/load/k6/${name}.summary.json" ]]; then
            mv "${ROOT_DIR}/load/k6/${name}.summary.json" "$out"
        fi
    fi

    if [[ $rc -eq 0 ]]; then
        echo "==> [$name] PASSED (report: $out)"
    else
        echo "==> [$name] FAILED rc=$rc (report: $out)"
    fi
}

# 실행 순서:
#   - read 시나리오 (full-text / facet) 를 의미 있게 하려면 색인된 데이터가 있어야 한다.
#     log-ingest 가 먼저 60s 동안 2000 req/s 로 풀어 OpenSearch 에 충분한 hit 을 만든다.
#   - multi-tenant-isolation 은 자체 publish 를 하므로 위치 의존 약함 — read 가 끝난 뒤에.
#   - alert-rule-eval 은 setup 에서 Sigma 룰을 import 하므로 마지막에. 시나리오 종료 후
#     룰이 alert_rules 테이블에 남는데 운영자가 정리하지 않으면 다음 실행에 누적된다
#     (overwriteByTitle=true 로 본 시나리오는 자기 자신을 덮어쓴다).
run_scenario "log-ingest"             "${SCENARIO_DIR}/log-ingest.js"
run_scenario "full-text-search"       "${SCENARIO_DIR}/full-text-search.js"
run_scenario "facet-aggregation"      "${SCENARIO_DIR}/facet-aggregation.js"
run_scenario "multi-tenant-isolation" "${SCENARIO_DIR}/multi-tenant-isolation.js"
run_scenario "alert-rule-eval"        "${SCENARIO_DIR}/alert-rule-eval.js"

echo
echo "==> 모든 시나리오 종료. 리포트: $REPORT_DIR"
ls -lah "$REPORT_DIR" 2>/dev/null || true
