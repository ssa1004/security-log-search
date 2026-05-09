# ADR-0004 Kafka 수집 + Flink 스트리밍

- 상태: Accepted
- 날짜: 2026-04-25

## 맥락

룰 기반 알람 평가 (예: "같은 IP 에서 5분 안 5회 인증 실패") 는 streaming 처리가 필요하다.
선택지는 크게 두 가지.

- Kafka Streams — Spring Kafka 와 같이 JVM 안에서 라이브러리로 동작. 운영이 간단.
- Apache Flink — 별도 cluster (jobmanager / taskmanager). state 백엔드 (RocksDB), exactly-
  once, savepoint, CEP 라이브러리.

## 검토한 대안

1. Kafka Streams — 운영 단순. 그러나 룰 변경 시 hot reload 가 까다롭고 (KStreams 는 룰을
   topology 에 박음), exactly-once 는 가능하나 multi-step pipeline 에서 까다로움. CEP
   라이브러리 부재.
2. Flink — 별도 cluster 운영 부담. 그러나 KeyedProcessFunction + MapState + Broadcast
   State 로 룰 hot reload 가 깔끔하고, savepoint 로 무중단 코드 업그레이드, CEP 라이브러리
   가 SEQUENCE / PATTERN 룰을 정의 가능.
3. Spark Structured Streaming — micro-batch 라 latency 가 큼 (수 초 단위). 본 도메인은
   초당 수만 이벤트의 brute-force 룰이라 sub-second latency 가 필요.

## 결정

Flink 채택 — 본 도메인이 룰 hot reload + 복잡 시퀀스 매칭 (SEQUENCE) + 운영 환경에서
exactly-once 보증을 모두 요구하기 때문.

핵심 패턴:

- {@code KeyedBroadcastProcessFunction} — 룰을 broadcast state 로, 이벤트를 keyed state
  로 받아 평가
- 룰 변경 채널 — Postgres alert_rules 테이블 → 별도 reader 가 Kafka {@code alert-rules.broadcast}
  topic 으로 publish → Flink job 이 broadcast 로 수신
- savepoint — 코드 변경 시 savepoint 로 stop → 새 버전으로 restore 하면 state 보존

운영 부담 완화:

- 단일 jobmanager + 1~2 taskmanager 로 시작 (수만 evt/s 까지 충분)
- state backend 는 RocksDB (대용량 keyed state)
- checkpoint storage 는 S3 또는 HDFS

## 결과

- 룰 추가 / 변경 시 Flink job 재시작 불필요 — 운영자가 REST 로 룰 만들면 < 5초 안 적용
- exactly-once — Kafka source / sink 도 transactional
- 별도 cluster (jobmanager / taskmanager) 운영 — Helm chart 로 패키징 (인프라 ADR 참고)

## 다시 검토할 시점

- 룰이 < 100개 + 단순 THRESHOLD 만 사용한다면 Kafka Streams 로 충분할 수도. 본 시스템은
  처음부터 SEQUENCE 룰 (brute-force 패턴) 을 1급 시민으로 두기로 결정해서 Flink.
- Apache Beam 으로 portable 하게 쓰는 옵션은 추후

## 참고

- [Flink CEP](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/libs/cep/)
- [Flink Broadcast State Pattern](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/fault-tolerance/broadcast_state/)
