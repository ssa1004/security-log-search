#!/usr/bin/env bash
# notification-hub stub.
#
# 운영에서는 https://github.com/ssa1004/notification-hub 가 alerts.fired 를 consume 해서
# 이메일 / Slack / SMS / push 채널로 운영자에게 발송합니다. 본 stub 은 그 부분을
# *console 출력* 로 대체 — alert 가 실제로 publish 됐는지만 검증.
#
# 출력은 docker compose logs 로 확인:
#   docker compose -f docker-compose.integration.yml logs -f mock-notification-hub
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
TOPIC="${ALERTS_TOPIC:-alerts.fired}"
GROUP="${CONSUMER_GROUP:-mock-notification-hub}"

echo "[mock-notification-hub] waiting for kafka at $BOOTSTRAP"
until kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list >/dev/null 2>&1; do
  sleep 2
done
echo "[mock-notification-hub] kafka ready"

# 토픽이 미리 있을 수도, 첫 publish 때 auto-create 될 수도 있음 — 명시 생성으로 안전장치.
kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
  --topic "$TOPIC" --partitions 3 --replication-factor 1 >/dev/null 2>&1 || true

echo "[mock-notification-hub] consuming '$TOPIC' (group=$GROUP) — alerts 가 들어오면 stdout 으로 출력"
exec kafka-console-consumer.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC" \
  --group "$GROUP" \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true \
  --property key.separator=' | '
