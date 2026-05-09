# ADR-0002 ECS vs OCSF — 둘 다 매핑하는 이유

- 상태: Accepted
- 날짜: 2026-04-23

## 맥락

보안 로그를 정규화하기 위한 표준 스키마가 두 개 경쟁한다.

- ECS (Elastic Common Schema) — Elastic 사가 정의한 보안 / 관측 로그 스키마. 8.x. 필드
  네이밍이 dotted (예: {@code event.action}, {@code source.ip}). Beats / Logstash 같은
  Elastic 생태계에서 native.
- OCSF (Open Cybersecurity Schema Framework) — OASIS 가 추진하는 벤더 중립 스키마. 1.x.
  필드 네이밍이 nested object (예: {@code src_endpoint.ip}, {@code actor.user.name}).
  Microsoft, Splunk, AWS, IBM 등이 공동 추진.

raw event source (방화벽 / EDR / 시스템 / 응용) 마다 export 하는 schema 가 다르다.
ECS-native 인 곳도 있고 OCSF-native 인 곳도 있다.

## 검토한 대안

1. ECS 만 채택 — Elastic 생태계가 가장 성숙. 그러나 OCSF native source 는 본 시스템
   바깥에서 변환해야 함.
2. OCSF 만 채택 — 벤더 중립 + 공동 추진의 미래 표준. 그러나 ECS 가 압도적으로 많이 쓰임
   (2026 시점).
3. 둘 다 1차 매핑하되 도메인 모델은 ECS 형태 — OCSF source 는 OCSF → ECS 매핑.

## 결정

대안 3 채택. 도메인의 {@link com.example.security.domain.event.LogEvent} 는 ECS 형태로
모델링하고, {@link com.example.security.domain.mapping.OcsfNormalizer} 가 OCSF payload 를
ECS 로 변환한다.

OCSF → ECS 핵심 매핑:

| OCSF | ECS |
|---|---|
| `class_uid=3002` (Authentication) | `event.category=authentication` |
| `activity_id=1` (Logon) | `event.action=logon` |
| `status_id=1/2` (Success/Failure) | `event.outcome=success/failure` |
| `severity_id=1~6` | `event.severity=0~100` |
| `time` (epoch ms) | `@timestamp` |
| `src_endpoint.ip` | `source.ip` |
| `actor.user.name` | `user.name` |
| `device.hostname` | `host.hostname` |

본 매핑은 OCSF 1.x 의 핵심 class 만 다룸 — 새 OCSF class 추가 시 OcsfNormalizer 확장.

## 결과

- 클라이언트는 schema 힌트 (`ecs` / `ocsf`) 만 보내면 됨
- 도메인 / 검색 / 집계는 ECS 형태로 일관 — OpenSearch 매핑 / ClickHouse 컬럼 정의가
  단순화됨
- 운영자는 ECS 만 알면 됨 (UI / 검색 query)
- OCSF source 추가 시 매핑 테이블만 갱신

## 다시 검토할 시점

- OCSF 가 정착 (Microsoft Sentinel / AWS Security Lake 가 OCSF native) 하면 도메인 모델
  자체를 OCSF 로 바꿀지 검토. 그 시점에는 ECS → OCSF 역방향 매핑이 필요해질 수도.
- Sigma rule import 추가 시 (Sigma 는 ECS 친화적) 매핑 정확성 다시 검증

## 참고

- [Elastic Common Schema 8.x](https://www.elastic.co/guide/en/ecs/current/index.html)
- [OCSF Schema Browser](https://schema.ocsf.io/)
