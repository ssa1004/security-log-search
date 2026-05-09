# ADR-0007 Multi-tenant 격리 — 4 layer

- 상태: Accepted
- 날짜: 2026-04-28

## 맥락

본 시스템은 다수 tenant (예: 고객사) 를 동시에 호스팅한다. tenant A 의 운영자가 tenant
B 의 보안 로그를 절대 보면 안 된다 — 보안 로그 자체가 침입 시도 정보를 포함하므로 leak
되면 추가 공격 표면이 된다.

## 검토한 대안

1. 단일 layer (e.g. JWT claim 만 검증) — application 코드 어디 한 군데에서 검증 누락 시
   바로 격리 깨짐
2. tenant 별 DB / cluster 완전 분리 — 격리는 강하나 운영 비용 폭발 (tenant 100개 = 100
   cluster)
3. defense-in-depth — 4 layer 중 하나가 깨져도 나머지가 막음

## 결정

대안 3 (4 layer) 채택. 각 layer 는 독립적으로 동작하며, 어느 한 곳이 무너져도 다른 곳
에서 차단된다.

### Layer 1 — OpenSearch 인덱스 / alias 격리

- 인덱스 이름 자체에 tenantId 박힘: `events-{tenant}-{seq}`
- read alias `events-{tenant}-read` 가 그 tenant 의 인덱스만 가리킴
- 다른 tenant 의 인덱스에 접근하려면 alias 를 우회해서 인덱스 이름을 직접 알아야 하는데
  application layer 가 alias 만 노출

### Layer 2 — ClickHouse Row Policy

- `WHERE tenant_id = currentSetting('tenant_id')` 가 모든 query 에 강제로 AND 결합
- ClickHouse 의 row-level security — DB engine 차원에서 강제

### Layer 3 — JWT claim

- 모든 요청은 `tenant_id` claim 을 가져야 함 (JWT 디코드 시 검증)
- {@link com.example.security.adapter.in.security.OperatorContextResolver} 가 추출

### Layer 4 — application layer query rewrite

- {@link com.example.security.application.service.SearchLogEventsService} 가 query 객체의
  tenantId 와 operator.tenantId 가 일치하는지 검증 (admin 우회 가능)
- OpenSearch 호출 시 BoolQuery filter 에 `tenant_id.keyword=tenantId` 강제 주입
- ClickHouse 호출 시 SQL parameter 로 `WHERE tenant_id = ?` 명시

## 결과

- 단일 layer 가 깨져도 다른 layer 가 막음 (defense-in-depth)
- 단일 클러스터 (OpenSearch / ClickHouse / Postgres) 운영 — 비용 효율
- audit_entries 가 tenant_mismatch 시도를 기록 — incident response

## 다시 검토할 시점

- 규제 요구로 tenant 데이터의 물리 분리 (별도 cluster) 가 필요해지면 layer 1/2 를
  cluster 분리로 강화
- query rewrite 의 자동 검증 (모든 query 에 tenant filter 가 들어갔는지 정적 분석) 도구

## 참고

- [ClickHouse Row Policy](https://clickhouse.com/docs/en/sql-reference/statements/create/row-policy)
- [OpenSearch Aliases](https://opensearch.org/docs/latest/im-plugin/index-alias/)
