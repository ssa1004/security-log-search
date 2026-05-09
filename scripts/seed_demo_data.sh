#!/usr/bin/env bash
# 데모 데이터 시드 — local Docker compose 환경 가정.
# 사용: ./scripts/seed_demo_data.sh [host:port]
set -euo pipefail
HOST="${1:-localhost:8080}"

echo "▶ 시드 시작: $HOST"

# 1. 추가 tenant onboard (acme 는 default 시드).
curl -s -X POST "http://$HOST/api/v1/tenants" \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "globex",
    "displayName": "Globex Corporation",
    "retention": "P730D",
    "hotRetention": "P14D",
    "piiPolicy": "STRICT"
  }' | jq

# 2. brute-force 룰 등록 (acme).
curl -s -X POST "http://$HOST/api/v1/alert-rules" \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "acme",
    "name": "5분 안 5회 인증 실패",
    "description": "brute-force 의심",
    "type": "THRESHOLD",
    "filterCategory": "authentication",
    "filterAction": "logon",
    "filterOutcome": "failure",
    "groupByField": "source.ip",
    "threshold": 5,
    "window": "PT5M",
    "severity": "HIGH",
    "enabled": true
  }' | jq

# 3. 더미 인증 실패 이벤트 5건 (같은 IP).
for i in $(seq 1 5); do
  curl -s -X POST "http://$HOST/api/v1/events" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: demo-evt-$i" \
    -d "{
      \"tenantId\": \"acme\",
      \"source\": \"firewall\",
      \"schema\": \"ecs\",
      \"occurredAt\": \"2026-05-09T12:00:0${i}Z\",
      \"payload\": {
        \"event.category\": \"authentication\",
        \"event.action\": \"logon\",
        \"event.outcome\": \"failure\",
        \"event.severity\": 70,
        \"source.ip\": \"192.168.1.10\",
        \"user.name\": \"alice\",
        \"message\": \"Failed login attempt $i\"
      }
    }" | jq
  sleep 0.2
done

echo "▶ 시드 완료. Flink 가 룰 평가 후 Kafka alerts.fired 로 알람 발행 — 곧 alerts 테이블에 INSERT 됨."
echo "▶ 알람 확인: curl -s 'http://$HOST/api/v1/alerts?tenantId=acme' | jq"
