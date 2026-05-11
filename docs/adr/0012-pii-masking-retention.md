# ADR-0012 PII 마스킹 + 보존 정책

- 상태: Accepted
- 날짜: 2026-05-03

## 맥락

보안 로그는 IP 주소 / 사용자 이름 / 이메일 / 호스트 이름 같은 개인정보 또는 PII (Personally
Identifiable Information) 를 포함한다. 운영자가 검색해서 본 결과를 export 할 때 마스킹이
필요하며, 옛 데이터는 보존기간 지난 후 자동 삭제 / 마스킹되어야 한다.

## 검토한 대안

1. 마스킹 안 함 — 단순하지만 개인정보보호법 위반 가능
2. 항상 마스킹 — 운영자 일이 어려워짐 (192.168.1.*** 만 보면 침해 사고 분석 어려움)
3. role 기반 마스킹 + 보존 기간별 마스킹

## 결정

대안 3 채택. 두 종류의 마스킹.

### 1. role 기반 (실시간) 마스킹

Tenant 별로 PII 정책 설정:

- `NONE` — 마스킹 안 함 (개발 / 테스트)
- `IP_ONLY` — IP 주소만 마지막 옥텟 마스킹 (`192.168.1.10` → `192.168.1.***`)
- `STRICT` — IP + 사용자명 (`alice` → `a***e`) + 이메일

운영자 role 별로 적용 :

| Role | 적용 정책 |
|---|---|
| OPERATOR | tenant.piiPolicy 적용 |
| ADMIN | 항상 NONE (전체 보임) |
| AUDITOR | STRICT (마스킹 강제) |

application layer 의 SearchService 가 결과 반환 직전에 마스킹 적용
(`com.example.security.domain.event.PiiMasker`).

### 2. 보존 기간별 마스킹

| 데이터 종류 | 90일 후 | 1년 후 |
|---|---|---|
| events_raw (ClickHouse) | (그대로) | TTL 13개월 후 DROP |
| OpenSearch 인덱스 | (그대로) | ILM 1년 후 delete |
| audit_entries | (그대로) | 2년 후 cold storage |

90일 후 마스킹은 별도 ClickHouse `ALTER TABLE ... UPDATE` 또는 새 테이블 + INSERT SELECT 로
처리하는 batch (월 1회). 운영 시 batch 가 무거우면 별도 column 으로 마스킹된 사본 보관.

### export 시 추가 검증

- `AuditAction.EXPORT_RESULTS` 가 audit_entries 에 기록됨
- export 결과 파일에 워터마크 (운영자 sub + 시각) 삽입 (옵션)

## 결과

- 운영자가 일상 검색 시 (OPERATOR role) 자동 마스킹 — 실수로 PII 노출 방지
- 침해 사고 조사 시 (ADMIN) 마스킹 해제로 정확한 분석
- 보존 기간 정책이 명시되어 개인정보보호법 준수

## 다시 검토할 시점

- 추가 PII 분류 (전화번호 / 주소 / 신용카드 번호) 가 raw event 에 들어오면 마스킹 정책
  확장
- 동적 redaction (검색 결과의 일부만 마스킹 해제) 요구가 생기면 별도 권한 분기

## 참고

- 개인정보보호법 (한국)
- GDPR Article 32 (security of processing) — pseudonymisation
