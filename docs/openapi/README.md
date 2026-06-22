# OpenAPI spec

> **English summary.** The OpenAPI 3 spec for the REST API is exported by booting the app and
> fetching `/v3/api-docs.yaml`. The app boots **with zero external infra** (default profile:
> H2 in-memory, OpenSearch/ClickHouse disabled, Kafka lazy), so the spec is generated and
> committed without Docker. CI regenerates it the same way and runs `git diff --exit-code`
> against the committed copy, so spec drift fails the build (drift gate). The `servers[0].url`
> is normalized to `http://localhost:8080` so the spec is independent of the (ephemeral) boot
> port. The fetched spec is then run through a deterministic **NORMALIZE** transform
> (`scripts/normalize-openapi.sh`) before it is committed/diffed — see *Determinism* below.
> Korean details below.

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

# 4) NORMALIZE — mapping key 재귀 정렬 + 모든 enum 배열 정렬 (canonical, 비결정성 제거)
./scripts/normalize-openapi.sh docs/openapi/security-log-search.yaml
```

> macOS 에는 `timeout` 이 없으므로 `/actuator/health` 를 폴링하는 bash while-loop 로
> UP 을 기다린 뒤 PID 를 kill 한다 (CI 의 `openapi-drift` job 도 동일). JAR 은 JDK21 로
> 컴파일되므로(Gradle 가 Foojay 로 21 provisioning) JDK17 호스트에서는
> `~/.gradle/jdks/.../jdk-21*/Contents/Home/bin/java` 로 부팅한다.

## Determinism (왜 NORMALIZE 가 필요한가)

springdoc 는 **enum 값 순서**와 `default:` 의 상대 위치를 환경마다 비결정적으로
내보낸다 — 예: `[FIVE_MINUTES, ONE_HOUR, ONE_DAY]` 의 순서가 로컬과 CI 에서 달라진다.
이는 실제 API 변경이 아니라 cosmetic ordering 이므로, raw byte-diff 게이트는
**false-positive (flaky)** 로 실패한다.

해결책은 spec 을 canonical 형태로 만드는 deterministic NORMALIZE 변환이다
(`scripts/normalize-openapi.sh`, mikefarah `yq` v4):

```bash
# 1) 모든 mapping 의 key 를 재귀 정렬       → key 순서 고정
yq -P -i '(.. | select(tag == "!!map")) |= sort_keys(.)' "$spec"
# 2) 모든 enum 배열 정렬 (enum 은 순서 없는 집합이므로 정렬은 의미적으로 안전)
yq -P -i '(.. | select(has("enum")).enum) |= sort' "$spec"
```

로컬 생성과 CI 게이트가 **같은 스크립트**를 쓰므로 양쪽 출력이 byte-identical 하다.
NORMALIZE 는 ordering 노이즈만 제거하고 endpoint / param / enum *값* 은 그대로 두므로,
게이트는 normalize 후에도 strict byte-diff 를 유지해 **실제 API surface 변경은 여전히 잡는다.**

검증: 서로 다른 두 포트에서 boot+fetch+normalize 를 두 번 돌려 출력이 byte-identical
(동일 sha256) 임을 확인했다 — 이것이 drift 게이트가 non-flaky 한 근거다.

> Gradle `org.springdoc.openapi-gradle-plugin` 의 `generateOpenApiDocs` 태스크도 같은 결과를
> 내지만, 본 모듈의 Kotlin top-level `main` 이 `...ApplicationKt` 로 컴파일되는 반면
> `springBoot.mainClass` 는 `...Application` 을 가리켜 forkedSpringBootRun 이 main 을 못 찾는다.
> 위 PropertiesLauncher override 방식이 zero-infra 부팅에 가장 확실하다.

CI (`.github/workflows/ci.yml` 의 `openapi-drift` job) 가 위와 동일하게 spec 을 재생성하고
**동일한 NORMALIZE 변환** (`scripts/normalize-openapi.sh`) 을 적용한 뒤
`git diff --exit-code docs/openapi/security-log-search.yaml` 로 drift 를 막는다.

## 보는 법

- Swagger UI — 앱 실행 후 `http://localhost:8080/swagger` (springdoc.swagger-ui.path)
- Redoc — `npx @redocly/cli preview-docs docs/openapi/security-log-search.yaml`
- 통합 뷰어 — profile repo `ssa1004/ssa1004` 의 `docs/api/index.html` (11 service spec 드롭다운)
