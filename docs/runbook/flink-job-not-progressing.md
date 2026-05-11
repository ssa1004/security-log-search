# Runbook — Flink correlation job 진행 정체

알람: `FlinkCorrelationJobLagging` (Kafka consumer lag 가 5만건 이상으로 5분간 지속) 또는
`FlinkCheckpointFailed` (마지막 성공 checkpoint 가 10분 이상 전).

처음 마주치는 경우 일반적으로 데이터가 멈춘 게 아니라 ack 가 멈춘 상황이라는 점을 먼저
의심한다 — Kafka 입장에서 producer 는 계속 publish 하는데 Flink 가 commit 을 못 하고 있는 경우.

## 1. 영향 확인

- Grafana — `Security Log Search → Flink Correlation` 대시보드 열기.
- 다음 패널 순서로 살펴본다:
  - "Records in/out per second" — input 이 떨어졌는지 (수집 측 문제) / output 이 떨어졌는지
    (Flink 측 문제).
  - "Consumer lag — events.normalized" — 어느 partition 에 쌓이고 있는지. 단일 partition 만
    쌓인다면 그 key 의 hot key 또는 단일 task slot 의 문제일 가능성.
  - "Checkpoint duration / failure" — 마지막 성공 checkpoint 시각, 실패 횟수.
  - "Backpressure ratio" — 0.5 이상이면 sink 측 (Kafka alerts.fired publish) 또는 외부
    호출이 막혀 있을 가능성.

## 2. 5분 우회

알람 평가가 잠시 멈추는 것은 수집 자체가 끊기는 것보다 지연 누적의 영향이 크다. 즉시
우회보다 root cause 를 찾는 데 집중. 다만 다음 두 경우는 즉시 조치:

- checkpoint 가 디스크 부족으로 실패 (`FAILURE_REASON: NO_SPACE_LEFT`) → checkpoint
  storage (S3 / HDFS) 의 quota 확인 및 증설.
- savepoint 가 깨져 job 자체가 RESTARTING 무한 루프 → 마지막 정상 savepoint 로 수동
  재시작 (5번 항목).

## 3. checkpoint 정상이지만 lag 증가 — 처리 속도 문제

병렬 처리가 부족한 상황. 순서대로:

1. **task slot 부하 확인**
   - Flink Web UI (`http://flink-jobmanager:8081`) → Job → Subtasks 탭.
   - 단일 subtask 의 `records in` 이 다른 subtask 대비 10배 이상 → **hot key** (특정
     `groupByField` 값에 트래픽 쏠림). brute-force 시도 IP 가 단일이면 정상이지만, 평소
     에 한 IP 가 95% 트래픽이면 룰의 `groupByField` 변경 검토.
   - `Backpressure` 가 모든 subtask 에서 RED → sink 측 또는 downstream 이 느림 → 4번 항목.

2. **parallelism 증설** (단기)
   - 현재 parallelism: `kubectl get flinkdeployment correlation-job -o yaml | grep parallelism`.
   - `kubectl edit flinkdeployment correlation-job` 에서 `spec.job.parallelism` 을 2배로
     올린 후 trigger savepoint → restart. partition 수 (`events.normalized` 의 partition
     ≥ parallelism) 가 충분한지 같이 확인.

3. **Kafka partition 추가** (중기)
   - parallelism 을 partition 수 이상으로 올려도 효과 X (Kafka source 의 max parallelism =
     partition 수).
   - `kafka-topics --bootstrap-server ... --alter --topic events.normalized --partitions 24`
     로 증설 (단방향 — 줄일 수 없음). consumer offset 재할당 영향 검토 후 진행.

## 4. Backpressure 가 sink 측 (alerts.fired) 에서 발생

- `kafka-topics --bootstrap-server ... --describe --topic alerts.fired` — partition leader / ISR
  정상 여부.
- broker disk usage / `BytesInPerSec` 메트릭 — Kafka 자체 capacity.
- alert volume 자체가 폭증한 것이라면 [alert-storm.md](alert-storm.md) 로.

## 5. job 자체가 RESTARTING 루프

`kubectl logs -n streaming flink-jobmanager-0 --tail=500 | grep -E "Job .* switched|RESTARTING|FAILED"` 로 cause exception 확인.

자주 발생하는 케이스:

- **상태 schema 호환성 깨짐** — domain `LogEvent` 또는 `AlertRule` 에 필드를 추가/삭제하면
  기존 savepoint 와 호환 깨짐. 해결: 마지막 정상 savepoint 가 있다면 거기서 재시작 +
  schema migration. 없다면 empty state 로 재시작 — 진행 중 알람 evaluation window 손실
  (1 window 의 false negative 리스크) 을 운영자가 수용해야 함.
  ```
  flink run -s s3://flink-savepoints/correlation/2026-05-09-1200 \
    -c com.example.security.streaming.AlertCorrelationJob \
    security-streaming-0.1.0.jar
  ```
- **broadcast state 의 룰 갯수 폭증** — 한 tenant 가 alert_rule 을 수만 개 등록해서
  broadcast state heap 폭증 → OOM. tenant 별 룰 수 상한 (현재 미구현, ADR-0008 의 다시
  검토 항목) 검토.
- **OutOfMemoryError: Direct buffer memory** — Kafka network buffer 부족. taskmanager 의
  `taskmanager.memory.network.fraction` 상향.

## 6. 회복 후

- consumer lag 가 정상 (< 1만건) 으로 복귀되면 자동 resolve.
- savepoint trigger 후 정상 종료 → restart 한 경우, 그 시점부터의 새 savepoint 가 잘
  생성되는지 30분 모니터링.
- Postmortem 항목:
  - lag 누적 시간 동안 알람 발화가 지연됐다. SOC 운영자에게 후처리 통보.
  - hot key / parallelism 부족이 원인이면 해당 룰의 `groupByField` 재설계 또는 capacity
    plan 갱신.

## 관련 문서

- ADR-0004 — Kafka + Flink 선택 이유
- ADR-0008 — Alert rule engine + broadcast state hot reload
- ADR-0009 — Backpressure 정책
- ADR-0015 — 운영 대시보드 (Flink Correlation 패널)
- 대시보드: `infrastructure/observability/grafana/dashboards/flink-correlation.json`
- 같이 보면 좋은 runbook: `alert-storm.md`
