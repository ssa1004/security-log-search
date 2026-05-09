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
- {@code QueryAuditLogUseCase} 가 검색 API 제공 (감사관 / 보안 담당자 용)

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

## 참고

- [PostgreSQL row-level trigger](https://www.postgresql.org/docs/current/sql-createtrigger.html)
- ISMS-P 2.9 보존 권고
