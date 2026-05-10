# notification-hub stub — alerts.fired Kafka topic 만 consume 해서 stdout 으로 출력.
# 운영: https://github.com/ssa1004/notification-hub
FROM bitnamilegacy/kafka:3.7

USER root
COPY consume-alerts.sh /opt/consume-alerts.sh
RUN chmod +x /opt/consume-alerts.sh

ENTRYPOINT ["/bin/bash", "/opt/consume-alerts.sh"]
