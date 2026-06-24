# ADR-0001 Hexagonal architecture + 모듈 분리

- 상태: Accepted
- 날짜: 2026-04-22

## 맥락

본 시스템은 두 가지 다른 실행 모델을 한 코드베이스에서 다룬다.

- Spring Boot 위에서 동작하는 REST API + Kafka consumer (events ingest, search, alert
  관리, audit query)
- Apache Flink runtime 위에서 동작하는 streaming job (events.normalized 를 받아 룰 평가)

도메인 모델 (LogEvent, AlertRule, Alert, Tenant, AuditEntry) 은 양쪽 모두에서 그대로 사용
된다. 도메인 코드를 한 모듈에 두고 두 실행 환경이 가져다 쓰는 구조가 필요하다.

## 검토한 대안

1. 단일 모듈에 모두 넣기 — Spring Boot starter 와 Flink starter 가 같은 classpath 에서 충돌.
   Flink connector-kafka 가 가져오는 kafka-clients 와 Spring Kafka 가 의존하는 버전이 다르고,
   Logback 도 Flink 가 자체 logging 을 가져온다.
2. 도메인 / application / adapter 만 모듈 분리, streaming 은 별도 repo — 도메인 모델이
   변경될 때마다 두 repo 를 동기화해야 함.
3. 7개 모듈로 분리, security-streaming 만 Spring Boot 미포함 별도 jar — 본 ADR 의 채택안.

## 결정

```
security-domain          (외부 의존성 0)
security-application     (use case + port, Spring 어노테이션만)
security-adapter-in      (REST + Kafka consumer)
security-adapter-out     (JPA + Kafka producer + OpenSearch + ClickHouse)
security-bootstrap       (Spring Boot main + Flyway + 초기 스키마)
security-streaming       (Flink job, Spring 미포함, fat-jar)
e2e-tests                (Testcontainers 통합 시나리오)
```

`security-streaming` 의 Flink connector / runtime 의존성은 다른 모듈에 영향 주지 않도록
`compileOnly` 또는 `provided` 로 처리. 의존성 그래프는 다음과 같이 단방향:

```
domain ← application ← adapter-out / adapter-in
                                ↑
                         bootstrap
streaming → domain (직접 의존만, application/adapter 는 안 가져감)
```

## 결과

- Spring 측 jar 크기 증가 없음 (Flink 의존성 안 들어옴)
- Flink streaming jar 도 Spring 어노테이션 / Hibernate / OpenSearch client 안 들어옴
- 도메인 모델 변경 시 두 곳 모두 동시에 컴파일 됨 (한 코드베이스)
- 모듈 경계는 build.gradle.kts 에 명시 (강제). IDE 에서 잘못 import 시 빌드 실패

## 다시 검토할 시점

- Spring Modulith 도입 검토 — 현재는 Gradle 모듈로만 경계 강제, Modulith 의 런타임
  경계 검사도 추가 가치 있을 수 있음
- Flink job 이 5개 이상으로 늘어나면 별도 repo 분리 검토 (CI 분리 + 배포 주기 분리)

## 용어 풀이 (쉽게)

- **헥사고날 아키텍처(Hexagonal, port/adapter)** — 핵심 로직을 한가운데 두고 DB·Kafka·웹은 콘센트(port)와 플러그(adapter)로만 연결해, 바깥을 바꿔도 핵심 코드는 안 건드리는 구조.
- **classpath 충돌** — 두 라이브러리(Spring·Flink)가 같은 부품의 서로 다른 버전을 들고 들어와, 같은 자리에서 부딪혀 프로그램이 깨지는 상황. 한 콘센트에 안 맞는 플러그를 억지로 꽂는 격.
- **fat-jar** — 실행에 필요한 외부 라이브러리까지 한 파일에 통째로 싸 넣은 실행 꾸러미. 짐을 따로따로 부치지 않고 큰 가방 하나에 다 넣는 셈.
- **compileOnly / provided** — 컴파일할 때만 빌려 쓰고 최종 결과물엔 안 담는 의존성 표시. Flink가 실행 환경에 이미 그 부품을 갖고 있어, 내 짐에 또 넣어 무겁게 만들 필요가 없을 때 쓴다.
- **단방향 의존성 그래프** — 모듈 사이 화살표가 한쪽(안쪽 도메인)으로만 향하고 거꾸로는 못 가게 막은 구조. 서로 맞물려 빙빙 도는 순환 의존을 막아 한 곳만 고쳐도 안전하다.
