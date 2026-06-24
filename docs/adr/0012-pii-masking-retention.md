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

## 용어 풀이 (쉽게)

- **마스킹(masking)** — 개인정보를 별표(***)로 가려 일부만 보이게 하는 것(`192.168.1.10` → `192.168.1.***`). 영수증의 카드번호 가운데를 가리는 셈.
- **role 기반 마스킹** — 보는 사람의 역할에 따라 가림 정도를 달리하는 것. 일반 운영자는 가려 보고, 사고 조사 권한자(ADMIN)는 전부 보이게 해 정확히 분석한다.
- **보존 정책 / retention / TTL** — 데이터를 정해진 기간만 두고 기한이 지나면 자동으로 삭제·마스킹하는 규칙(유통기한 지난 건 자동 파쇄). ClickHouse는 TTL, OpenSearch는 ILM이 담당.
- **pseudonymisation(가명처리)** — 누구인지 바로 못 알아보게 이름·식별자를 가짜 값으로 바꾸되, 꼭 필요하면 되돌릴 여지는 남기는 처리. GDPR이 권하는 개인정보 보호 기법.
- **dynamic redaction(동적 부분 공개)** — 가려진 결과 중 '일부 칸만' 권한에 따라 그때그때 풀어 보여 주는 것. 통째로 다 가리거나 다 푸는 게 아니라 필요한 부분만 선택적으로 연다.
- **watermark(워터마크)** — 내보낸(export) 파일에 '누가 언제 받았는지'를 은은히 새겨 두는 것. 유출 시 출처를 역추적하게 하는 보이지 않는 도장.

## 참고

- 개인정보보호법 (한국)
- GDPR Article 32 (security of processing) — pseudonymisation
