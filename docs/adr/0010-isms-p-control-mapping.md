# ADR-0010 ISMS-P 통제 매핑

- 상태: Accepted
- 날짜: 2026-05-01

## 맥락

ISMS-P (정보보호 및 개인정보보호 관리체계 인증) 는 한국 KISA 의 보안 / 개인정보 인증 체계
다. 본 시스템은 SIEM 형태 보안 로그 플랫폼으로, ISMS-P 인증을 받는 조직이 사용할 수 있어야
한다. 통제 항목별로 본 시스템이 어떻게 대응하는지 매트릭스를 명시한다.

## 결정

다음 매트릭스를 도입. 본 표는 운영 가이드 / 인증 심사 자료의 1차 자료가 된다.

| ISMS-P 통제 | 요구사항 요약 | 본 시스템의 구현 |
|---|---|---|
| 2.5 사용자 식별 / 인증 | 모든 사용자 식별 + 인증 | OAuth2 Resource Server + JWT (sub claim 으로 식별) |
| 2.5.4 비밀번호 관리 | 패스워드 정책 | 본 시스템 외부 (IDP 가 담당) |
| 2.6 네트워크 접근 통제 | 네트워크 분리 / 접근 제어 | TLS 1.3 + mTLS for Kafka, Spring Security filter, K8s NetworkPolicy |
| 2.7 암호화 적용 | at-rest + in-transit | Postgres TDE + ClickHouse encrypted disk + OpenSearch encryption-at-rest, TLS 1.3 |
| 2.8 백업 / 복구 | 정기 백업 + 복구 절차 | Postgres pg_basebackup, ClickHouse BACKUP/RESTORE, OpenSearch snapshot to S3 |
| 2.9 감사 / 추적 | 사용자 활동 기록 | `audit_entries` 테이블 (append-only) + 5년 보존, Kafka SIEM sink |
| 2.10 사고 대응 | 침해사고 탐지 / 대응 | Flink correlation rule + alert workflow + on-call notification |
| 3.1 개인정보 수집 / 이용 | 최소 수집 + 동의 | PII 정책 (Tenant 별 NONE/IP_ONLY/STRICT) + 마스킹 |
| 3.4 개인정보 파기 | 보존기간 후 파기 | OpenSearch ILM delete (1년) + ClickHouse TTL (13개월), `audit_entries` 5년 후 cold storage |
| 3.5 정보주체 권리 | 열람 / 정정 / 파기 요청 | 본 시스템 외부 (별도 portal) |

### 2.9 감사 통제의 구체 구현

| 동작 | audit_entries.action |
|---|---|
| raw event 수집 (운영자 디버그 호출 시) | INGEST |
| 운영자 검색 | SEARCH |
| 운영자 통계 query | STATS_QUERY |
| 룰 생성 / 수정 / 삭제 | RULE_CREATED / RULE_UPDATED / RULE_DELETED |
| 알람 발화 (Flink job) | ALERT_FIRED |
| 알람 처리 | ALERT_ACKNOWLEDGED / RESOLVED / FALSE_POSITIVE |
| 인덱스 관리 | INDEX_CREATED / INDEX_ROLLOVER / ALIAS_SWAP / ILM_POLICY_APPLIED |
| 테넌트 라이프사이클 | TENANT_ONBOARDED / TENANT_DEACTIVATED |
| 플랫폼 관리자의 본인 외 tenant 접근 | CROSS_TENANT_ACCESS |
| 운영자 로그인 / export | LOGIN_OPERATOR / EXPORT_RESULTS |

> INGEST 는 대량 트래픽 (이벤트 수집) 이라 평소엔 audit 에 남기지 않는다 — noise. raw event
> 의 정규화 디버그 같은 운영자 명시 호출 시에만 기록한다. ALERT_FIRED 는 운영자가 아닌 시스템
> (Flink correlation job) 이 actor 이며, 이후 운영자의 ALERT_ACKNOWLEDGED 와 구분된다.

> CROSS_TENANT_ACCESS — 일반 운영자의 tenant 불일치는 거부 (`TenantMismatchException`) 되지만
> PLATFORM_ADMIN 은 모든 tenant 접근이 허용된다 (ADR-0007 Layer 4 admin 우회). 이 우회 자체가
> 2.6 (접근 통제) 의 추적 대상이라, 본인 외 tenant 의 검색 / 통계 / 알람 / 룰 / 인덱스 / 감사
> 로그 접근 시 `CROSS_TENANT_ACCESS` 를 남긴다 (`CrossTenantAccessAudit`). tenant 라이프사이클은
> 이미 TENANT_ONBOARDED / TENANT_DEACTIVATED 가 같은 역할을 하므로 별도로 남기지 않는다.

각 audit_entries row 는 actor (subject) + actor_role + source_ip + occurred_at + target +
details 를 포함 — "누가 언제 어디서 무엇을 했는가" 를 명확히.

## 결과

- ISMS-P 인증 심사 시 본 매트릭스 + audit_entries 샘플로 통제 항목 별 구현 증빙 가능
- 정기 self-audit 시 본 ADR 을 체크리스트로 사용

## 다시 검토할 시점

- ISMS-P 개정 시 (보통 2~3년 주기) 매트릭스 재검토
- 추가 인증 (ISO 27001, SOC 2) 도입 시 별도 매트릭스 추가

## 용어 풀이 (쉽게)

- **ISMS-P** — 한국 KISA가 운영하는 정보보호·개인정보보호 인증 제도. '이 조직이 보안·개인정보를 규정대로 관리한다'를 심사로 받는 자격증 같은 것이라, 통제 항목마다 어떻게 대응하는지 증빙이 필요하다.
- **통제 항목 매핑(control mapping)** — 인증이 요구하는 항목 하나하나에 '우리 시스템은 이렇게 충족한다'를 짝지어 적은 표. 시험 출제 범위에 내 답안을 칸칸이 맞춰 놓는 셈.
- **SIEM (보안 정보·이벤트 관리)** — 여러 곳의 보안 로그를 한데 모아 검색·상관분석·알람을 돌리는 보안 관제 플랫폼. 본 시스템이 바로 이 SIEM 역할을 한다(CCTV 영상을 한 관제실에 모아 보는 격).
- **append-only audit(추가 전용 감사 로그)** — 한 번 적으면 수정·삭제 절대 안 하고 새 줄만 쌓는 기록(은행 통장). 사고를 한참 뒤에 조사해도 변조 안 된 기록이 있어야 믿을 수 있다.
- **OAuth2 Resource Server + JWT(sub claim)** — 요청에 딸려 온 로그인 토큰(JWT)을 검증해 보호 자원을 내주는 서버. sub claim은 토큰 안의 '이 사람이 누구인가' 식별자로, 누가 무엇을 했는지를 이걸로 가린다.
- **mTLS(상호 TLS)** — 보통 TLS는 서버만 신분증을 보이지만, mTLS는 클라이언트도 신분증을 같이 보여 서로 신원을 확인하는 방식. 양쪽이 다 출입증을 대는 셈.
- **at-rest / in-transit 암호화** — at-rest는 디스크에 '저장된 상태'의 데이터를, in-transit은 네트워크로 '오가는 중'의 데이터를 암호화하는 것. 금고 안과 운송 트럭 안 둘 다 잠그는 격.
- **CROSS_TENANT_ACCESS** — 플랫폼 관리자가 예외로 다른 회사 데이터에 접근한 사실을 별도로 남기는 감사 항목. 허용은 하되 '이 접근이 있었다'를 추적용으로 꼭 기록한다.

## 참고

- [한국 KISA — ISMS-P 인증](https://isms.kisa.or.kr/)
- 개인정보보호법 — 정보주체 권리 / 보존기간
