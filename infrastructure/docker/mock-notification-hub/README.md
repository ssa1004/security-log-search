# mock-notification-hub

`docker-compose.integration.yml` 의 notification-hub stub.

운영에서는 [notification-hub](https://github.com/ssa1004/notification-hub) 가
본 repo 의 `alerts.fired` Kafka topic 을 consume 해서 이메일 / Slack / SMS / push 채널로
운영자에게 발송합니다. 본 stub 은 그 발송 부분을 console 출력으로 대체합니다.
"alert 가 정말 publish 됐고 외부에서 consume 가능한가" 만 통합 시연에서 검증.

`consume-alerts.sh` 는 bitnami/kafka 이미지에 포함된 `kafka-console-consumer.sh`
한 줄을 그룹 ID 와 함께 띄웁니다. 받은 메시지는 stdout 으로 흘러가고 docker compose
logs 로 확인합니다:

```bash
docker compose -f infrastructure/docker/docker-compose.integration.yml logs -f mock-notification-hub
```
