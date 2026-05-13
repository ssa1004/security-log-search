# OWASP API Security Top 10 (2023) 매핑

본 문서는 SIEM (Security Information and Event Management) 도메인 — 멀티테넌트 보안 로그
플랫폼 — 의 API 표면이 OWASP API Top 10 (2023) 위협에 어떻게 대응하는지를 항목별로
정리한다. SOC 운영자 / 외부 위협 인텔리전스 ingest agent 모두 본 API 의 소비자다.

본 매핑은 [ADR-0007 multi-tenant 격리 4 layer](../adr/0007-multi-tenant-isolation.md) 와
[ADR-0010 ISMS-P 통제 매핑](../adr/0010-isms-p-control-mapping.md) 의 상위 문서로
동작한다. 위협 항목 → 본 시스템의 통제 → 회귀 테스트 위치를 1:1 로 추적한다.

## 멀티테넌트 격리 — 4 layer (요약)

본 항목은 OWASP API1 / API3 / API5 모두에 걸친 횡단 통제다. 자세한 설계는 ADR-0007 참고.

| Layer | 통제 위치 | 효과 |
|---|---|---|
| 1. OpenSearch alias | `events-{tenant}-read` alias 가 그 tenant 의 인덱스만 가리킴 (`OpenSearchEventSearchAdapter` 가 alias 만 호출) | tenant B 의 인덱스가 alias 에 없으면 query 가 0건 반환 |
| 2. ClickHouse Row Policy | `WHERE tenant_id = currentSetting('tenant_id')` 가 모든 query 에 강제 AND (`ClickHouseRowPolicyProvisioner`) | DB engine 차원에서 tenant 우회 차단 |
| 3. JWT claim | `tenant_id` claim 필수 검증 (`OperatorContextResolver`) | claim 없으면 즉시 401 |
| 4. Application query rewrite | `OperatorContext.tenantId` 와 query 의 tenantId 일치 검증 + OpenSearch BoolQuery 의 `filter` 에 `tenant_id.keyword=tenantId` 강제 주입 | application 코드가 last line of defense |

## 항목별 매핑

### API1 — Broken Object Level Authorization (BOLA)

위협: tenant A 의 운영자가 tenant B 의 event/alert/rule 의 ID 를 직접 알아 우회 접근.

| 표면 | 통제 위치 | 회귀 테스트 |
|---|---|---|
| `POST /api/v1/search` | `SearchLogEventsService.enforceTenant` — operator.tenantId 와 query.tenantId 일치 검증, 우회 시 audit 후 `TenantMismatchException` (HTTP 403) | `SearchLogEventsServiceTest#다른_tenant_요청은_거부_audit_기록` |
| `GET /api/v1/alerts` | `ListAlertsService.enforceTenant` (list / acknowledge / resolve / false-positive 전부) | `ListAlertsServiceTest` |
| `POST /api/v1/alerts/{id}/ack` 외 transition | `ListAlertsService.transition` — alert 의 tenantId 와 operator.tenantId 일치 검증 (path-id 만 받아도 안전) | `ListAlertsServiceTest` |
| `POST /api/v1/alert-rules` / PUT / DELETE | `DefineAlertRuleService.enforceTenant` — rule 의 tenantId 일치 검증 | `DefineAlertRuleServiceTest` |
| `GET /api/v1/audit` | `QueryAuditLogService` — operator.tenantId 일치 검증 | `QueryAuditLogService` (코드 직접 검증) |
| `GET /api/v1/stats` | `AggregateLogStatsService` — operator.tenantId 일치 검증, ClickHouse SQL parameter `WHERE tenant_id = ?` 강제 | `AggregateLogStatsService` |

`PLATFORM_ADMIN` role 은 모든 tenant 접근 가능 — 운영 / 인시던트 대응 용도. (`OperatorContext.canQueryOtherTenant()`)

### API2 — Broken Authentication

위협: JWT 검증 누락 / weak signing / tenant claim 우회.

| 통제 | 위치 |
|---|---|
| OAuth2 Resource Server | `SecurityConfig.prod` (`@Profile("prod")`) — Spring Security 의 `oauth2ResourceServer.jwt()` |
| `issuer-uri` 검증 | `application.yml` 의 `spring.security.oauth2.resourceserver.jwt.issuer-uri` (운영 환경에서 IDP URL 필수) |
| stateless session | `SessionCreationPolicy.STATELESS` — 세션 hijacking 방지 |
| `tenant_id` claim 필수 | `OperatorContextResolver.currentOperator()` — claim 누락 시 `IllegalStateException` |
| `permitAll` 화이트리스트 | `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger` 만 — 그 외 모든 endpoint 는 인증 필요 |
| dev profile fallback | `SecurityConfig.dev` (`@Profile("!prod")`) — 로컬 / 통합 테스트 한정. `OperatorContextResolver` 가 anonymous + PLATFORM_ADMIN fallback (운영에서는 절대 사용 금지 — profile 검증으로 차단) |

### API3 — Broken Object Property Level Authorization

위협: 검색 결과 / 알람 detail 에 마스킹되어야 할 PII (개인정보) / 민감 필드가 그대로 노출.

| 통제 | 위치 | 회귀 테스트 |
|---|---|---|
| Tenant 별 PII 정책 | `Tenant.PiiMaskingPolicy` — `NONE` / `IP_ONLY` / `STRICT` 3단계 | `PiiMaskerTest` |
| 검색 결과 마스킹 | `SearchLogEventsService.search` — OpenSearch 응답을 `PiiMasker.mask` 로 마스킹 후 audit 도 마스킹 결과 카운트만 기록 | `SearchLogEventsServiceTest#정상_검색은_PII_마스킹_적용` |
| IP 마지막 옥텟 마스킹 | `PiiMasker.maskIp` (IPv4 last octet → `***`, IPv6 last group → `****`) | `PiiMaskerTest` |
| username 마스킹 | `PiiMasker.maskUser` (alice → `a***e`) | `PiiMaskerTest` |
| message 안 이메일 마스킹 | `PiiMasker.maskEmailInMessage` (정규식 치환 → `***@***`) | `PiiMaskerTest` |
| audit log 의 query 본문 | `SearchLogEventsService.auditSearch` — `query.luceneQueryString()` 만 기록, hit 본문은 기록 안 함 (audit 조회권자가 PII 우회로 hit 보지 못함) | code 검증 |

### API4 — Unrestricted Resource Consumption

위협: 검색 한 번에 수천만 건 dump / facet aggregation 으로 cluster 부하 유발 / 무한 cursor.

| 통제 | 위치 |
|---|---|
| 검색 페이지 크기 | `SearchQuery` 의 `size` — 1~1000 강제 (`SearchQuery` constructor 가 validate), DTO 단에서 `@Min(1) @Max(1000)` |
| facet 크기 | `SearchQuery.facetSize` — 0~100 강제 |
| facet 필드 화이트리스트 | `OpenSearchEventSearchAdapter.ALLOWED_TERM_FIELDS` — `event_kind` / `event_category` / `event_type` / `event_action` / `event_outcome` / `severity` / `source_ip` / `destination_ip` / `user_name` / `host_name` / `host_os` 만 허용 (`requireAllowedField` 검증). 임의 필드 (예: 내부 control 필드 / 다른 tenant 의 `_id`) 조회 차단 |
| cursor pagination | `OpenSearchEventSearchAdapter` — `search_after` 토큰 형식 검증 (`\|` 구분) |
| audit 조회 페이지 크기 | `QueryAuditLogUseCase.AuditQuery.MAX_SIZE = 1000` — record constructor 가 validate, 컨트롤러 단에서도 `@Min(1) @Max(MAX_SIZE)` |
| 알람 timeline 페이지 크기 | `ListAlertsUseCase.ListAlertsQuery.MAX_SIZE = 500` — record constructor + 컨트롤러 단 검증 |
| stats topN | `StatsController.topN` — `@Min(1) @Max(1000)` |
| Sigma YAML 상한 | `SigmaImportRequest.yaml` — `@Size(max = 5MB)`. parser 자체는 `LoaderOptions.maxAliasesForCollections=50` 으로 billion-laughs 차단 |
| HTTP 본문 상한 | `application.yml` 의 `server.tomcat.max-http-form-post-size=5MB` |
| ClickHouse SQL injection | `ClickHouseEventStatsAdapter.sanitizeColumn` — 컬럼명 영숫자 + underscore 만 허용. tenantId / from / to / filter value 는 PreparedStatement parameter |
| 외부 호출 회복성 | Resilience4j circuit breaker / retry / bulkhead (`opensearch`, `clickhouse`) — 단일 tenant 의 폭주로 다른 tenant 영향 차단 |

### API5 — Broken Function Level Authorization

위협: 일반 `OPERATOR` role 운영자가 admin endpoint (인덱스 rollover / tenant onboarding /
ILM 적용) 를 호출하여 운영 자원을 변경.

| 통제 | 위치 | 회귀 테스트 |
|---|---|---|
| 인덱스 admin (`POST /api/v1/admin/indices/{tenant}/*`) | `ManageOpenSearchIndexService.enforceAdmin` — `ADMIN` role 필수, 본 tenant 만 (또는 PLATFORM_ADMIN) | `ManageOpenSearchIndexServiceTest` |
| 테넌트 라이프사이클 (`POST/DELETE /api/v1/tenants`) | `OnboardTenantService.enforcePlatformAdmin` — `PLATFORM_ADMIN` role 필수 | `OnboardTenantServiceTest` |
| 룰 CRUD | `DefineAlertRuleService.enforceTenant` — 본 tenant 만 (ADMIN role 자체는 `OPERATOR` 와 같은 layer 에서 작동) | `DefineAlertRuleServiceTest` |
| Sigma rule import | `ImportSigmaRuleService.enforceTenant` — 본 tenant 만 | `ImportSigmaRuleServiceTest` |
| HTTP 403 응답 | `InsufficientPrivilegeException` → `GlobalExceptionHandler` 의 `insufficient_privilege` problem detail | — |

### API6 — Unrestricted Access to Sensitive Business Flows

위협: 운영자가 검색 API 로 다른 tenant 의 행동 패턴을 enumeration / scrape.

| 통제 | 위치 |
|---|---|
| 모든 검색 audit 기록 | `SearchLogEventsService.auditSearch` — 누가 (`actor`) 언제 어떤 query 로 몇 건 받았는지 기록 (ISMS-P 2.9) |
| tenant 우회 시도 audit | `SearchLogEventsService.enforceTenant` — 거부 자체도 audit 에 `tenant_mismatch` 로 기록 → SOC 가 SIEM 으로 본 시스템을 다시 감시 가능 (recursive monitoring) |
| stats query audit | `AggregateLogStatsService` — 모든 stats query 도 audit |
| 룰 변경 audit | `DefineAlertRuleService.auditChange` — RULE_CREATED / RULE_UPDATED / RULE_DELETED |
| 알람 처리 audit | `ListAlertsService.transition` — ALERT_ACKNOWLEDGED / RESOLVED / FALSE_POSITIVE |
| 인덱스 admin audit | `ManageOpenSearchIndexService.appendAudit` — INDEX_CREATED / INDEX_ROLLOVER / ILM_POLICY_APPLIED |
| Tenant 라이프사이클 audit | `OnboardTenantService` — TENANT_ONBOARDED / TENANT_DEACTIVATED |

검색 빈도 제한 / 시간당 query 상한 같은 rate limit 은 본 시스템 외부 (API gateway / WAF
layer) 에서 처리 — 본 서비스는 SOC tooling 으로 빠른 검색이 정상 사용 패턴이라 application
단 rate limit 은 적용 안 함.

### API7 — Server Side Request Forgery (SSRF)

위협: 외부 로그 source (CloudTrail / K8s audit / 외부 webhook) 를 URL 로 받아서 pull 할 때
임의 URL 로 redirect 되어 내부 네트워크 자원 접근.

| 표면 | 통제 |
|---|---|
| `POST /api/v1/events/cloudtrail` | **Push only** — `SourceIngestController.cloudtrail` 은 payload 본문을 직접 받음. 외부 URL 을 받아서 fetch 하지 않음. |
| `POST /api/v1/events/k8s-audit` | **Push only** — 동일. agent 가 audit log 를 push 하는 형태. |
| `POST /api/v1/events` (일반) | **Push only** — `IngestController` 는 payload 를 직접 받음. |
| Sigma rule import | `SigmaImportRequest.yaml` 자체를 받음 — URL 받지 않음. 외부 SigmaHQ ruleset 을 가져오려면 클라이언트가 직접 fetch 후 본 API 로 POST. |
| ClickHouse / OpenSearch / Kafka URL | 설정 (application.yml / 환경변수) 으로만 주입 — 외부 입력으로 결정되지 않음. |

SSRF 표면이 현재 존재하지 않음. 향후 외부 source pull (예: S3 CloudTrail bucket polling) 을
도입하면 다음을 강제할 것:
- URL host 화이트리스트 (S3 endpoint / customer VPC peering 만 허용)
- IMDSv2 / link-local / RFC1918 차단
- DNS rebinding 방지 (한 번 resolve 한 IP 만 사용)

### API8 — Security Misconfiguration

| 통제 | 위치 |
|---|---|
| Actuator 노출 | `application.yml` 의 `management.endpoints.web.exposure.include=health,info,prometheus,metrics` — `/actuator/env` / `/actuator/heapdump` / `/actuator/loggers` 비노출 |
| health detail | `management.endpoint.health.show-details=when-authorized` — 인증된 운영자만 상세 (downstream 의존성 노출 방지) |
| 운영 DB 자격증명 | `application.yml` 의 `${DB_PASSWORD:...}` 환경변수 주입. 기본값 약함 → 운영 환경은 K8s Secret 으로 강제 (Helm chart) |
| CSRF | API 는 stateless JWT 기반 — `csrf.disable()` (REST API 일반 패턴) |
| Session | `SessionCreationPolicy.STATELESS` |
| TLS / mTLS | K8s Ingress + Service Mesh (외부 인프라 layer) |
| OpenSearch / ClickHouse default 보안 | `security.opensearch.enabled` / `security.clickhouse.enabled` flag — 운영 (`prod` profile) 만 활성, 로컬 / 테스트는 비활성. 운영은 IaC (Helm / Terraform) 가 HTTPS + auth 강제 |
| Hibernate ddl-auto | `validate` — runtime 에 schema 변경 차단. 변경은 Flyway migration 으로만 |
| open-in-view | `false` — N+1 query / late session leak 차단 |
| Error response | `GlobalExceptionHandler` — RFC 7807 problem detail. 스택트레이스 / 내부 SQL 노출 안 함 |

### API9 — Improper Inventory Management

| 통제 | 위치 |
|---|---|
| API 버전 명시 | 모든 endpoint 가 `/api/v1/*` prefix — 향후 v2 도입 시 v1 deprecation 절차 가능 |
| OpenAPI 문서 | `springdoc` — `/v3/api-docs` (운영자 / API 소비자가 인증 후 inventory 확인 가능) |
| Swagger UI | `/swagger` — 운영 환경 활성화 (운영자 도구) — 외부 노출 시 ingress / WAF 가 IP 제한 |
| Deprecated endpoint | 현재 없음. 도입 시 OpenAPI `deprecated: true` + 6개월 후 제거 절차 (Backstage catalog-info.yaml 추적) |

### API10 — Unsafe Consumption of APIs

위협: 외부 위협 인텔리전스 (SigmaHQ public ruleset 등) 의 Sigma 룰 YAML 을 import 할 때
임의 Java object deserialization / 미지원 표현으로 인한 silent skip.

| 통제 | 위치 | 회귀 테스트 |
|---|---|---|
| YAML safe parsing | `SigmaYamlParser` — SnakeYAML 의 `SafeConstructor` 만 사용 → 임의 Java type instantiate 차단 (RCE 방지) | `SigmaYamlParserTest` |
| YAML alias 폭탄 차단 | `LoaderOptions.maxAliasesForCollections=50` — billion-laughs 류 DoS 차단 | `SigmaYamlParserTest` |
| YAML 본문 상한 | `SigmaImportRequest.yaml` — `@Size(max=5MB)` (DoS 차단) | DTO validation |
| field modifier 미지원 명시 | `SigmaToAlertRuleMapper.map` — `equals` / `contains` / `startswith` / `endswith` 만 인식, 나머지는 `unsupported` 에 기록. 클라이언트가 응답의 `mappingNotes` 로 즉시 확인 | `SigmaToAlertRuleMapperTest` |
| 다중 selection silent skip 방지 | `SigmaToAlertRuleMapper.map` — condition 이 다중 selection 참조 시 `unsupported` 기록 + rule `enabled=false` 로 import (운영자 수동 검토 강제) | `SigmaToAlertRuleMapperTest` |
| aggregation / timeframe 미지원 명시 | `containsAggregation` / detection `timeframe` 키 → `unsupported` 기록 + rule disabled | `SigmaToAlertRuleMapperTest` |

## ISMS-P 2.6 (접근 통제) 와 cross-reference

OWASP API 통제와 ISMS-P 통제는 다음과 같이 매핑된다 (자세한 매트릭스는
[ADR-0010](../adr/0010-isms-p-control-mapping.md)).

| ISMS-P 통제 | 연관 OWASP API |
|---|---|
| 2.5 사용자 식별 / 인증 | API2 (Broken Authentication) |
| 2.6 네트워크 / 시스템 접근 통제 | API1 (BOLA) + API5 (Function Level Authz) — `OperatorContext` 의 tenant + role 검증이 application layer 의 접근 통제 구현 |
| 2.9 감사 / 추적 | API6 (Sensitive Flow) — 모든 운영자 행동 audit 기록 |
| 3.1 / 3.4 개인정보 보호 | API3 (Object Property) — `PiiMasker` |

## 발견 이슈 (2026-05-13)

본 sweep 에서 발견 + fix 한 항목:

1. **API5** — `ManageOpenSearchIndexService` 의 admin endpoint (createInitialIndex / triggerRollover / applyIlmPolicy) 가 role 검증 없음 → `enforceAdmin` 추가 (`ADMIN` role 필수, 본 tenant 또는 PLATFORM_ADMIN). 회귀 테스트 `ManageOpenSearchIndexServiceTest` 추가.
2. **API5** — `OnboardTenantService.onboard` / `deactivate` 가 role 검증 없음 → `enforcePlatformAdmin` 추가 (`PLATFORM_ADMIN` role 필수). 회귀 테스트 `OnboardTenantServiceTest` 추가.
3. **API4** — `QueryAuditLogUseCase.AuditQuery.size` upper bound 없음 → `MAX_SIZE = 1000` 강제. `AuditController` 에서 `@Min/@Max` + `@Validated`.
4. **API4** — `ListAlertsUseCase.ListAlertsQuery.size` upper bound 없음 → `MAX_SIZE = 500` 강제. `AlertController` 에서 `@Min/@Max` + `@Validated`.
5. **API4** — `StatsController.topN` upper bound 없음 → `@Min(1) @Max(1000)` + `@Validated`.
6. **API4** — `SigmaImportRequest.yaml` 크기 상한 없음 → `@Size(max=5MB)` 추가.
7. **API4** — HTTP 본문 상한 명시 → `server.tomcat.max-http-form-post-size=5MB`.

`InsufficientPrivilegeException` (HTTP 403 `insufficient_privilege`) 신설 — function-level
authorization 실패 시 일관된 응답.

## 운영 시 다시 검토할 시점

- 외부 source pull (S3 CloudTrail polling 등) 도입 시 → API7 (SSRF) 통제 추가 필요
- v2 API 도입 시 → API9 (Inventory) 의 deprecation 절차 명시
- SIEM 데이터를 외부로 export 하는 API 도입 시 → audit 의 `EXPORT_RESULTS` action 활성화 + API6 의 export rate limit 검토

## 참고

- [OWASP API Security Top 10 (2023)](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [ADR-0007 multi-tenant 격리 4 layer](../adr/0007-multi-tenant-isolation.md)
- [ADR-0010 ISMS-P 통제 매핑](../adr/0010-isms-p-control-mapping.md)
- [ADR-0012 PII 마스킹 / 보존 정책](../adr/0012-pii-masking-retention.md)
- [ADR-0013 Sigma rule import](../adr/0013-sigma-rule-import.md)
