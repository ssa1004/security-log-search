#!/usr/bin/env bash
# Sigma → AlertRule → Flink → Alert 흐름 데모.
#
# 1. scripts/sample-sigma-rules/*.yml 4 건을 multi-document YAML 로 합쳐 한 번의
#    POST /api/v1/sigma-rules 로 import (parser 가 multi-document YAML 지원).
# 2. 응답의 createdRules 갯수 / mappingNotes (unsupported) 를 확인.
# 3. import 결과 alert_rules 목록 (GET /api/v1/alert-rules?tenantId=acme) 확인 — 변환된
#    AlertRule 들이 등록됐는지.
# 4. 변환된 alert_rule 중 brute-force 패턴을 트리거할 인증 실패 이벤트 5건 발사.
# 5. Flink job 이 broadcast state 로 룰을 받아 평가 → Kafka alerts.fired publish →
#    Spring 측 consumer 가 alerts 테이블 INSERT. 운영자는 GET /api/v1/alerts 로 확인.
#
# 사용:
#   ./scripts/import_sigma_demo.sh                  # default localhost:8080 + tenant acme
#   ./scripts/import_sigma_demo.sh prod.example:80 globex
#
# 의존성: bash 4+, curl, jq, yq (mikefarah/yq v4+).
#   - yq 가 없으면 multi-document 합치는 부분만 python3 으로 대체 가능 (주석 참고).

set -euo pipefail

HOST="${1:-localhost:8080}"
TENANT="${2:-acme}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULES_DIR="$SCRIPT_DIR/sample-sigma-rules"

if ! command -v jq >/dev/null 2>&1; then
  echo "[error] jq 가 필요합니다." >&2
  exit 1
fi

echo "[1/5] sample-sigma-rules 디렉토리 확인"
ls "$RULES_DIR"/*.yml >/dev/null
echo "  → $(ls "$RULES_DIR"/*.yml | wc -l | tr -d ' ') 개 룰 파일 확인"

echo "[2/5] multi-document YAML 로 결합"
# YAML document 구분자 (---) 로 단순 concat — Sigma parser 가 multi-document 지원.
COMBINED=$(awk 'FNR==1 && NR!=1 {print "---"} {print}' "$RULES_DIR"/*.yml)

echo "[3/5] POST /api/v1/sigma-rules — import (overwriteByTitle=true)"
RESP=$(jq -n \
  --arg tenant "$TENANT" \
  --arg yaml "$COMBINED" \
  '{tenantId: $tenant, yaml: $yaml, overwriteByTitle: true}' \
  | curl -s -X POST "http://$HOST/api/v1/sigma-rules" \
      -H 'Content-Type: application/json' \
      --data-binary @-)

echo "$RESP" | jq

CREATED=$(echo "$RESP" | jq -r '.createdCount // 0')
echo "  → $CREATED 개 AlertRule 생성됨"

UNSUPPORTED_TOTAL=$(echo "$RESP" | jq '[.mappingNotes[].unsupported | length] | add // 0')
if [ "$UNSUPPORTED_TOTAL" -gt 0 ]; then
  echo "  → 변환 한계 (unsupported) 총 $UNSUPPORTED_TOTAL 건 — 운영자 검토 필요:"
  echo "$RESP" | jq -r '.mappingNotes[] | select(.unsupported|length>0) |
    "    - rule \(.alertRuleId): \(.unsupported | join("; "))"'
fi

echo "[4/5] GET /api/v1/alert-rules?tenantId=$TENANT — 변환된 룰 확인"
curl -s "http://$HOST/api/v1/alert-rules?tenantId=$TENANT" | jq \
  '[.[] | {ruleId, name, severity, enabled, threshold, window, groupByField}]'

echo "[5/5] brute-force 트리거 — 같은 IP 에서 5회 인증 실패 이벤트"
for i in $(seq 1 5); do
  curl -s -X POST "http://$HOST/api/v1/events" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: sigma-demo-evt-$i" \
    -d "{
      \"tenantId\": \"$TENANT\",
      \"source\": \"firewall\",
      \"schema\": \"ecs\",
      \"occurredAt\": \"2026-05-09T12:00:0${i}Z\",
      \"payload\": {
        \"event.category\": \"authentication\",
        \"event.action\": \"logon\",
        \"event.outcome\": \"failure\",
        \"event.severity\": 70,
        \"source.ip\": \"203.0.113.42\",
        \"user.name\": \"alice\",
        \"message\": \"sigma-demo failed login $i\"
      }
    }" >/dev/null
  sleep 0.2
done
echo "  → 5건 publish 완료. Flink job 이 broadcast state 로 룰을 적용한 뒤 alerts.fired 발화."
echo
echo "알람 확인 (Flink 가 Kafka 로 publish 후 Spring consumer 가 INSERT 까지 1-3초):"
echo "  curl -s 'http://$HOST/api/v1/alerts?tenantId=$TENANT' | jq"
