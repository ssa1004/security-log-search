# OpenAPI spec

> **English summary.** The OpenAPI 3 spec for the REST API is exported by booting the app and
> fetching `/v3/api-docs.yaml`. The app boots **with zero external infra** (default profile:
> H2 in-memory, OpenSearch/ClickHouse disabled, Kafka lazy), so the spec is generated and
> committed without Docker. CI regenerates it the same way and runs `git diff --exit-code`
> against the committed copy, so spec drift fails the build (drift gate). The `servers[0].url`
> is normalized to `http://localhost:8080` so the spec is independent of the (ephemeral) boot
> port. Korean details below.

`security-log-search` 의 REST API 를 OpenAPI 3 spec 으로 build-time export 한다.

## 무엇이 들어가나

- `security-log-search.yaml` — 빌드 시 생성되는 OpenAPI 3 문서. 외부 참조 / SDK codegen 의 단일 진실값.
  - 로그 수집 (`/api/v1/ingest`, source 별 ingest)
  - 검색 / 통계 (`/api/v1/search`, `/api/v1/stats`)
  - 알람 / 알람 룰 / Sigma 룰 (`/api/v1/alerts`, `/api/v1/alert-rules`, `/api/v1/sigma-rules`)
  - 테넌트 / 인덱스 운영 / 감사 (`/api/v1/tenants`, `/api/v1/admin/indices`, `/api/v1/audit`)

> 이 디렉토리의 `*.yaml` 은 앱을 부팅해 생성·갱신한다. 로컬에서 수기로 편집하지 않는다.

## 생성 방법

앱은 **외부 인프라 없이** 부팅된다 (기본 profile: H2 in-memory, OpenSearch/ClickHouse
disabled, Kafka lazy). 따라서 Docker 없이도 spec 을 생성할 수 있다. 부팅 후
`/v3/api-docs.yaml` 을 받아 `docs/openapi/security-log-search.yaml` 로 저장한다.

```bash
# 1) boot jar 생성
./gradlew :security-bootstrap:bootJar

# 2) zero-infra 로 부팅 (top-level Kotlin main → ...ApplicationKt). 빈 high 포트 사용.
JAR=security-bootstrap/build/libs/security-bootstrap-0.1.0-boot.jar
java -Dloader.main=com.example.security.SecurityLogSearchApplicationKt \
     -cp "$JAR" org.springframework.boot.loader.launch.PropertiesLauncher \
     --server.port=18099 &

# 3) /actuator/health 가 UP 이 되면 spec fetch → servers[0].url 을 8080 으로 정규화
curl -s http://localhost:18099/v3/api-docs.yaml \
  | sed 's#http://localhost:18099#http://localhost:8080#' \
  > docs/openapi/security-log-search.yaml
```

> Gradle `org.springdoc.openapi-gradle-plugin` 의 `generateOpenApiDocs` 태스크도 같은 결과를
> 내지만, 본 모듈의 Kotlin top-level `main` 이 `...ApplicationKt` 로 컴파일되는 반면
> `springBoot.mainClass` 는 `...Application` 을 가리켜 forkedSpringBootRun 이 main 을 못 찾는다.
> 위 PropertiesLauncher override 방식이 zero-infra 부팅에 가장 확실하다.

CI (`.github/workflows/ci.yml` 의 `openapi-drift` job) 가 위와 동일하게 spec 을 재생성한 뒤
`git diff --exit-code docs/openapi/security-log-search.yaml` 로 drift 를 막는다.

## 보는 법

- Swagger UI — 앱 실행 후 `http://localhost:8080/swagger` (springdoc.swagger-ui.path)
- Redoc — `npx @redocly/cli preview-docs docs/openapi/security-log-search.yaml`
- 통합 뷰어 — profile repo `ssa1004/ssa1004` 의 `docs/api/index.html` (11 service spec 드롭다운)
