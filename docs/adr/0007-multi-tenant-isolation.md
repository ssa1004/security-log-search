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
- `com.example.security.adapter.in.security.OperatorContextResolver` 가 추출

### Layer 4 — application layer query rewrite

- `com.example.security.application.service.SearchLogEventsService` 가 query 객체의
  tenantId 와 operator.tenantId 가 일치하는지 검증 (admin 우회 가능)
- OpenSearch 호출 시 BoolQuery filter 에 `tenant_id.keyword=tenantId` 강제 주입
- ClickHouse 호출 시 SQL parameter 로 `WHERE tenant_id = ?` 명시

## 결과

- 단일 layer 가 깨져도 다른 layer 가 막음 (defense-in-depth)
- 단일 클러스터 (OpenSearch / ClickHouse / Postgres) 운영 — 비용 효율
- audit_entries 가 두 가지를 기록 — incident response 용:
  - 일반 운영자의 tenant_mismatch 우회 시도 (거부됨)
  - PLATFORM_ADMIN 의 본인 외 tenant 접근 (허용되지만 `CROSS_TENANT_ACCESS` 로 추적, ADR-0010)

## 다시 검토할 시점

- 규제 요구로 tenant 데이터의 물리 분리 (별도 cluster) 가 필요해지면 layer 1/2 를
  cluster 분리로 강화
- query rewrite 의 자동 검증 (모든 query 에 tenant filter 가 들어갔는지 정적 분석) 도구

## 용어 풀이 (쉽게)

- **멀티테넌트(multi-tenant) 격리** — 한 서버에 여러 회사(tenant)가 같이 사는데 A사가 B사 데이터를 절대 못 보게 치는 칸막이. 한 건물에 여러 세입자가 살되 남의 집 문은 못 여는 것.
- **defense-in-depth(다중 방어)** — 한 겹만 두면 코드 한 줄 실수로 뚫리니, 방어막을 여러 겹 겹쳐 한 겹이 뚫려도 다음 겹이 막게 하는 것. 성문·해자·내성을 겹겹이 두는 셈.
- **ClickHouse Row Policy(행 정책)** — DB 자체가 '너는 네 회사 행만'이라고 모든 쿼리에 자동으로 조건을 끼워 넣는 기능. 개발자가 조건을 깜빡해도 DB가 막아 준다.
- **query rewrite(쿼리 강제 재작성)** — 사용자가 보낸 검색 조건에 서버가 몰래 '그리고 네 회사 것만(AND tenant=내회사)'을 강제로 덧붙여 다시 쓰는 것.
- **JWT claim(클레임)** — 로그인 토큰(JWT) 안에 박혀 있는 '이 사람은 어느 회사 소속' 같은 신원 항목. 위변조하면 검증에서 걸려, 토큰만 보고 소속을 안전하게 안다.
- **PLATFORM_ADMIN 우회 + 추적(CROSS_TENANT_ACCESS)** — 플랫폼 관리자만 예외로 다른 회사 데이터에 접근할 수 있되, 그 접근 하나하나를 감사 기록으로 남겨 나중에 따질 수 있게 하는 것.
- **incident response(사고 대응)** — 보안 사고가 났을 때 '누가 무엇을 했나'를 추적해 원인을 밝히고 수습하는 활동. 그래서 우회 시도·접근을 기록으로 남긴다.

## 참고

- [ClickHouse Row Policy](https://clickhouse.com/docs/en/sql-reference/statements/create/row-policy)
- [OpenSearch Aliases](https://opensearch.org/docs/latest/im-plugin/index-alias/)
