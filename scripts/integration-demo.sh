#!/usr/bin/env bash
# Cross-repo 통합 시연.
#
# 운영 portfolio 에서 본 repo 의 통합점:
#   1) auth-service 가 발급한 JWT 으로 인증 → 본 repo 의 검색 API 호출 (멀티테넌트 격리)
#   2) Sigma 룰 import 후 트리거 이벤트 → alerts.fired publish → notification-hub 가 consume
#
# 본 스크립트는 위 두 흐름을 mock 으로 검증합니다.
#   - auth-service: docker-compose.integration.yml 의 mock-auth (nginx 가 JWK Set 노출)
#   - notification-hub: 같은 compose 의 mock-notification-hub (alerts.fired 만 consume)
#
# 사전:
#   docker compose -f infrastructure/docker/docker-compose.integration.yml up -d
#   # security-app health 가 UP 될 때까지 대기 (보통 30-60초)
#
# 사용:
#   ./scripts/integration-demo.sh                    # default localhost:8080
#   ./scripts/integration-demo.sh prod.example:80
#
# 검증 포인트:
#   - acme 토큰으로 globex 데이터 조회 시 0건 (멀티테넌트 격리 4 layer)
#   - Sigma 매칭 → alerts 테이블 INSERT
#   - alerts.fired Kafka topic 에 publish 되어 mock-notification-hub stdout 에 표시
#     ($ docker compose -f .../docker-compose.integration.yml logs mock-notification-hub)
#
# 의존성: bash 4+, curl, jq, openssl.
set -euo pipefail

HOST="${1:-localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PRIVATE_KEY="$REPO_ROOT/infrastructure/docker/mock-auth/private-key.pem"
RULES_DIR="$SCRIPT_DIR/sample-sigma-rules"
ISSUER="http://mock-auth:8080"     # security-app 컨테이너에서 본 mock-auth 의 issuer
KID="integration-demo-key"

for cmd in curl jq openssl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[error] $cmd 이 필요합니다." >&2
    exit 1
  fi
done

if [ ! -f "$PRIVATE_KEY" ]; then
  echo "[error] private key 가 없습니다: $PRIVATE_KEY" >&2
  echo "        infrastructure/docker/mock-auth/ 디렉토리 확인." >&2
  exit 1
fi

# base64url — RFC 7515 §2 (= 패딩 제거, +/ → -_).
b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

# mint_jwt <subject> <tenant_id>
#   RS256, kid=integration-demo-key, exp=+1h.
mint_jwt() {
  local subject="$1"
  local tenant="$2"
  local now exp header payload header_b64 payload_b64 signing_input sig

  now=$(date -u +%s)
  exp=$((now + 3600))

  header=$(jq -nc --arg kid "$KID" '{alg:"RS256", typ:"JWT", kid:$kid}')
  payload=$(jq -nc \
    --arg iss "$ISSUER" \
    --arg sub "$subject" \
    --arg tenant "$tenant" \
    --argjson iat "$now" \
    --argjson exp "$exp" \
    '{iss:$iss, sub:$sub, iat:$iat, exp:$exp, tenant_id:$tenant, scope:"search.read events.write rules.write"}')

  header_b64=$(printf '%s' "$header" | b64url)
  payload_b64=$(printf '%s' "$payload" | b64url)
  signing_input="${header_b64}.${payload_b64}"

  sig=$(printf '%s' "$signing_input" \
    | openssl dgst -sha256 -sign "$PRIVATE_KEY" \
    | b64url)

  printf '%s.%s' "$signing_input" "$sig"
}

# auth_curl <method> <path> [data]
auth_curl() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local token="${TOKEN:?TOKEN 미정의}"
  if [ -n "$data" ]; then
    curl -sf -X "$method" "http://$HOST$path" \
      -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' \
      -d "$data"
  else
    curl -sf -X "$method" "http://$HOST$path" \
      -H "Authorization: Bearer $token"
  fi
}

echo "[1/7] security-app 헬스체크 — http://$HOST/actuator/health"
if ! curl -sf "http://$HOST/actuator/health" | jq -e '.status == "UP"' >/dev/null; then
  echo "[error] security-app 가 UP 상태가 아닙니다." >&2
  echo "        docker compose -f infrastructure/docker/docker-compose.integration.yml up -d 후 30-60초 기다리세요." >&2
  exit 1
fi
echo "  → UP"

echo "[2/7] mock JWT 발급 — sub=demo-operator, tenant_id=acme"
TOKEN_ACME=$(mint_jwt "demo-operator" "acme")
TOKEN_GLOBEX=$(mint_jwt "demo-operator" "globex")
echo "  → acme 토큰 ${#TOKEN_ACME} bytes / globex 토큰 ${#TOKEN_GLOBEX} bytes"

echo "[3/7] 두 tenant onboard (acme 는 default 시드일 수 있어 409 도 OK)"
TOKEN="$TOKEN_ACME"
auth_curl POST /api/v1/tenants \
  '{"tenantId":"acme","displayName":"ACME","retention":"P365D","hotRetention":"P7D","piiPolicy":"STANDARD"}' \
  >/dev/null 2>&1 || echo "  → acme 이미 존재 (skip)"
auth_curl POST /api/v1/tenants \
  '{"tenantId":"globex","displayName":"Globex","retention":"P730D","hotRetention":"P14D","piiPolicy":"STRICT"}' \
  >/dev/null 2>&1 || echo "  → globex 이미 존재 (skip)"
echo "  → onboard 완료"

echo "[4/7] 멀티테넌트 격리 시연 — globex 토큰으로 acme 데이터 조회 시도"
echo "       먼저 acme 토큰으로 이벤트 1건 publish"
TOKEN="$TOKEN_ACME"
auth_curl POST /api/v1/events '{
  "tenantId": "acme",
  "source": "firewall",
  "schema": "ecs",
  "occurredAt": "2026-05-09T11:30:00Z",
  "payload": {
    "event.category": "authentication",
    "event.action": "logon",
    "event.outcome": "success",
    "source.ip": "10.0.0.7",
    "user.name": "demo-operator-acme",
    "message": "integration demo — acme tenant 전용"
  }
}' >/dev/null
sleep 2

echo "       acme 토큰으로 검색 (자기 데이터)"
ACME_HITS=$(auth_curl POST /api/v1/search '{"query":"*","size":10}' \
  | jq '.hits | length' 2>/dev/null || echo 0)
echo "       → acme 본인 데이터: $ACME_HITS 건"

echo "       globex 토큰으로 같은 검색 — query rewrite 가 tenantId:globex 강제"
TOKEN="$TOKEN_GLOBEX"
GLOBEX_HITS=$(auth_curl POST /api/v1/search '{"query":"*","size":10}' \
  | jq '.hits | length' 2>/dev/null || echo 0)
echo "       → globex 가 보는 acme 데이터: $GLOBEX_HITS 건 (격리되었다면 0)"
if [ "$GLOBEX_HITS" -ne 0 ] 2>/dev/null; then
  echo "[warn] 멀티테넌트 격리가 깨졌거나 globex 자체 데이터가 있을 수 있음 — 수동 확인" >&2
fi

echo "[5/7] Sigma 룰 import — sample 4건 multi-document YAML"
TOKEN="$TOKEN_ACME"
COMBINED=$(awk 'FNR==1 && NR!=1 {print "---"} {print}' "$RULES_DIR"/*.yml)
RESP=$(jq -n --arg yaml "$COMBINED" \
  '{tenantId:"acme", yaml:$yaml, overwriteByTitle:true}' \
  | curl -sf -X POST "http://$HOST/api/v1/sigma-rules" \
      -H "Authorization: Bearer $TOKEN" \
      -H 'Content-Type: application/json' \
      --data-binary @-)
CREATED=$(echo "$RESP" | jq -r '.createdCount // 0')
echo "  → $CREATED 개 AlertRule 생성"

echo "[6/7] 트리거 이벤트 5건 — 같은 IP 인증 실패 (brute-force 패턴)"
for i in $(seq 1 5); do
  auth_curl POST /api/v1/events "{
    \"tenantId\": \"acme\",
    \"source\": \"firewall\",
    \"schema\": \"ecs\",
    \"occurredAt\": \"2026-05-09T12:00:0${i}Z\",
    \"payload\": {
      \"event.category\": \"authentication\",
      \"event.action\": \"logon\",
      \"event.outcome\": \"failure\",
      \"event.severity\": 70,
      \"source.ip\": \"203.0.113.99\",
      \"user.name\": \"alice\",
      \"message\": \"integration-demo failed login $i\"
    }
  }" >/dev/null
  sleep 0.2
done
echo "  → publish 완료"

echo "[7/7] alerts 테이블 polling — 최대 15초"
ALERTS=0
for _ in $(seq 1 15); do
  ALERTS=$(auth_curl GET '/api/v1/alerts?tenantId=acme' \
    | jq '. | length' 2>/dev/null || echo 0)
  if [ "$ALERTS" -gt 0 ] 2>/dev/null; then
    break
  fi
  sleep 1
done
echo "  → alerts.fired → INSERT 까지 도달한 알람: $ALERTS 건"

echo
echo "=========================================="
echo "통합 시연 완료. 검증 포인트:"
echo "  - JWT 검증: mock-auth 의 JWK Set 으로 security-app 가 토큰 통과"
echo "  - 멀티테넌트 격리: acme 데이터=$ACME_HITS, globex 가 본 acme=$GLOBEX_HITS"
echo "  - Sigma 룰 import: $CREATED 건"
echo "  - alerts.fired publish: $ALERTS 건 INSERT"
echo
echo "notification-hub 가 같은 알람을 받았는지 확인:"
echo "  docker compose -f infrastructure/docker/docker-compose.integration.yml logs mock-notification-hub | tail -20"
echo "=========================================="
