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

- `KeyedBroadcastProcessFunction` — 룰을 broadcast state 로, 이벤트를 keyed state
  로 받아 평가
- 룰 변경 채널 — Postgres alert_rules 테이블 → 별도 reader 가 Kafka `alert-rules.broadcast`
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

## 용어 풀이 (쉽게)

- **스트리밍 처리 / Apache Flink** — 끊임없이 흘러 들어오는 로그 강물을 멈추지 않고 실시간 분석하는 엔진. 쌓아뒀다 나중에 보는 게 아니라 흐르는 동안 바로 패턴을 보고 경보(컨베이어벨트 위 물건을 지나갈 때 즉시 검사).
- **Kafka Streams** — 별도 클러스터 없이 앱 안에 라이브러리로 끼워 넣어 돌리는 가벼운 스트림 처리 도구. 운영은 쉽지만 규칙을 바꾸려면 손이 많이 간다.
- **exactly-once(정확히 한 번)** — 장애가 나 재처리가 일어나도 한 이벤트가 결과에 딱 한 번만 반영되게 하는 보장. 같은 입금이 두 번 잡히거나 한 번도 안 잡히는 일을 막는다.
- **hot reload(핫 리로드)** — 프로그램을 끄지 않고 켜진 상태 그대로 설정·규칙만 갈아끼우는 것. 차 안 세우고 달리면서 내비 목적지만 바꾸는 셈.
- **Broadcast State(브로드캐스트 상태)** — 탐지 규칙 목록을 모든 일꾼에게 똑같이 '방송'해 다 같이 갖게 하는 것. 규칙은 모두가 공유하고 로그 데이터만 나눠 처리한다(사장이 새 규정을 전 직원에게 단체 공지).
- **KeyedBroadcastProcessFunction / keyBy** — 로그를 회사(tenant)별로 줄 세워(keyBy) 같은 회사 건은 같은 일꾼이 처리하게 묶고, 거기에 방송된 규칙을 합쳐 평가하는 Flink 핵심 부품.
- **CEP (Complex Event Processing)** — 흩어진 여러 이벤트의 '순서·패턴'을 한 사건으로 알아채는 기능('실패 여러 번 뒤 성공' 같은 침입 패턴).
- **THRESHOLD / SEQUENCE 룰** — THRESHOLD는 '몇 분 안 몇 번 이상이면 경보'(횟수). SEQUENCE는 '이 일 직후 저 일이면 경보'(순서). 무차별 대입(brute-force) 탐지에 둘 다 쓴다.
- **state backend / RocksDB** — 스트리밍이 '최근 5분 카운트' 같은 중간 기억을 보관하는 창고. RocksDB는 그 기억이 메모리에 다 안 들어갈 만큼 클 때 디스크에 얹어 두는 저장 엔진.
- **checkpoint / savepoint** — 처리 상태를 통째로 떠 두는 스냅샷. checkpoint는 장애 복구용 자동 저장, savepoint는 코드를 새 버전으로 바꿀 때 기억을 잃지 않게 손으로 떠 두는 '이어하기' 저장점.
- **micro-batch(마이크로 배치)** — 진짜 실시간이 아니라 '아주 짧은 묶음(수 초)'으로 모았다 처리하는 방식. 그만큼 지연이 생겨 초 단위 즉시 탐지엔 불리하다.

## 참고

- [Flink CEP](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/libs/cep/)
- [Flink Broadcast State Pattern](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/dev/datastream/fault-tolerance/broadcast_state/)
