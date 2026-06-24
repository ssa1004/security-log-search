# ADR-0011 Audit log — append-only PostgreSQL + 보존 5년

- 상태: Accepted
- 날짜: 2026-05-02

## 맥락

ISMS-P 2.9 / SOC 2 / GDPR 등 대부분의 보안 / 컴플라이언스 요구는 audit log 의 무결성을 요구
한다. "누가 언제 어떤 검색 / 룰 변경 / 알람 처리를 했는가" 가 변조 불가능하게 기록되어야
한다.

## 검토한 대안

1. application 로그 (`logger.info`) — 표준 로그 파일에 섞여서 무결성 보장 약함, 검색
   어려움
2. 별도 audit DB + UPDATE/DELETE 차단 trigger — 무결성 보장 + 구조화된 query 가능
3. blockchain / immutable storage (예: AWS QLDB) — 강한 무결성이지만 운영 부담 + 본 도메인
   에서 과한 수준
4. write-ahead log + checksum — 자체 구현은 위험

## 결정

대안 2 채택. PostgreSQL `audit_entries` 테이블에 append-only 로 기록.

### 무결성 보장

- application layer 에서는 INSERT 만 허용 (Repository 인터페이스에 UPDATE/DELETE 노출 안 함)
- DB role 차원에서도 audit_entries 에 대해 UPDATE/DELETE 권한 없음 (운영 환경에서 별도
  GRANT 로 강제)
- 추가 옵션: trigger 로 UPDATE/DELETE 시도 시 raise exception (운영 환경)

### 보존 정책

- 5년 보존 (ISMS-P 권고, 일부 산업은 7년 / 10년)
- 2년 이상 된 데이터는 cold storage (S3 archival) 로 이관 (별도 batch)
- 운영 DB 의 hot 데이터는 최근 2년만 유지

### 검색 가능성

- 인덱스: (tenant_id, occurred_at), actor, action
- `QueryAuditLogUseCase` 가 검색 API 제공 (감사관 / 보안 담당자 용)

### 별도 SIEM sink (옵션)

- audit_entries INSERT 와 동시에 Kafka topic 으로 publish
- 외부 SIEM (조직의 중앙 보안 시스템) 에 forward
- 본 시스템의 audit 와 외부 SIEM 의 cross-check

## 결과

- 모든 운영자 행위가 audit_entries 에 1:1 row 로 기록됨
- 검색 / 룰 변경 / 알람 처리 후 5초 내 audit query 로 확인 가능
- DB 백업이 곧 audit 백업

## 다시 검토할 시점

- audit_entries 가 폭증 (일 1억 row) 하면 별도 ClickHouse 또는 S3 Parquet 이관 검토
- WORM (Write Once Read Many) storage 요구가 들어오면 AWS S3 Object Lock 또는 QLDB 검토

## 용어 풀이 (쉽게)

- **append-only(추가 전용)** — 한 번 적으면 수정·삭제 절대 안 하고 새 줄만 쌓는 장부(은행 통장). 잘못된 게 있으면 지우는 대신 반대 기록을 새 줄로 더한다.
- **무결성(integrity)** — '기록이 처음 적힌 그대로이고 누가 몰래 고치지 않았다'는 신뢰성. 감사 로그의 생명이라, 고치거나 지우지 못하게 막아 이를 지킨다.
- **trigger(트리거)** — DB에서 특정 동작(UPDATE/DELETE 시도)이 일어나면 자동으로 끼어들어 막거나 다른 일을 하게 하는 장치. '이 서랍 열면 경보 울림' 같은 자동 반응.
- **DB role / GRANT(권한)** — 'audit 테이블엔 INSERT만 되고 UPDATE·DELETE는 아예 권한 없음'처럼 계정별로 할 수 있는 일을 DB가 못 박는 것. 코드가 실수해도 권한이 없어 막힌다.
- **immutable storage / blockchain(QLDB)** — 한 번 쓰면 못 바꾸는 저장소. 블록체인·AWS QLDB가 이런 강한 불변성을 주지만 운영이 무거워, 본 도메인엔 과해서 보류한다.
- **WORM (Write Once Read Many) / S3 Object Lock** — '한 번 쓰면 여러 번 읽기만 되고 덮어쓰기·삭제는 금지'인 저장 모드. S3 Object Lock이 파일에 그 잠금을 걸어 규제 보존을 강제한다.
- **cold storage(콜드 스토리지)** — 거의 안 보는 오래된 데이터를 싸고 느린 보관소(S3 등)로 옮겨 두는 것. 자주 안 꺼내는 짐을 창고로 내리는 셈.
- **SIEM sink / forward(전달)** — 감사 기록을 조직 중앙 보안 시스템(SIEM)으로도 흘려보내(forward) 두 곳이 서로 대조(cross-check)하게 하는 보조 출구.

## 참고

- [PostgreSQL row-level trigger](https://www.postgresql.org/docs/current/sql-createtrigger.html)
- ISMS-P 2.9 보존 권고
