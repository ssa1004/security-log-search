# Contributing

본 저장소의 개발 흐름과 commit 규칙을 정리한 문서입니다.

## 브랜치 전략

GitHub Flow 를 따릅니다. `main` 은 항상 배포 가능한 상태로 유지되며, 모든 작업은 feature
브랜치에서 진행됩니다.

```
main (protected)
  ├── feature/ocsf-mapper          ← 기능 브랜치
  ├── fix/clickhouse-row-policy
  └── docs/update-adr-0007
```

흐름은 `git checkout -b feature/<짧은-설명>` → 작업 → PR → 코드 리뷰 + CI 통과 → Squash and
merge 입니다. 머지 후 feature 브랜치는 즉시 삭제합니다.

## Commit 메시지

Conventional Commits 형식을 따릅니다.

```
<type>(<scope>): <짧은 설명, 50자 이내>

<상세 설명, 한 줄에 72자 이내>
- 무엇이 / 왜 변경되었는지
- 영향받는 모듈
```

사용하는 type: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`.
scope 에는 모듈명 (`domain`, `application`, `adapter-out`, `streaming`, `bootstrap` 등) 이
들어갑니다.

ECS / OCSF 정규화, 멀티테넌트 격리, Flink correlation rule 이 도메인의 핵심이므로 관련
commit 이 자주 발생합니다.

### 예시

```
feat(streaming): Flink KeyedProcessFunction 으로 brute-force 룰 평가

- BruteForceCorrelator: source.ip 별 5분 슬라이딩 윈도우
- MapState 에 실패 카운트 누적, TimerService 로 윈도우 만료 처리
- 임계값 초과 + 직후 성공 발견 시 alerts.fired 로 publish
```

```
fix(adapter-out): OpenSearch read alias 가 다른 tenant 인덱스를 가리키던 버그

events-acme-read alias 가 onboarding 시 actions.add.indices 에 모든 인덱스를
포함시키던 문제. tenant prefix 매칭 (events-{tenant}-*) 으로 좁힘.
```

## Commit 단위

한 commit 은 한 가지 논리적 변경을 담는 것을 원칙으로 합니다. 새 기능 + 리팩터링 + 버그
수정이 한 commit 에 같이 포함되어 있다면 거의 항상 분리 가능합니다. WIP commit 은 PR 머지
전에 squash 합니다.

## 테스트

PR 전 `./gradlew check` 통과가 필수입니다.

- 도메인 단위: `:security-domain:test`
- application 단위: `:security-application:test`
- adapter 단위 (mock 기반): `:security-adapter-out:test`, `:security-adapter-in:test`
- Flink 단위 (LocalExecutionEnvironment): `:security-streaming:test`
- 통합 시나리오 (Testcontainers, Docker 필요): `:e2e-tests:integrationTest`

Testcontainers 통합 테스트는 Docker 미가용 환경에서는 자동 skip 됩니다. 단위 테스트는
환경 무관하게 항상 통과해야 합니다.

## 코드 스타일

- Java: Google Java Format 또는 IntelliJ default.
- 주석 / 문서는 자연스러운 한국어 (영어 직역체 지양).
- 전문 용어 옆에 짧은 한국어 풀이를 함께 적습니다 (예: ECS — Elastic Common Schema, 보안 /
  관측 로그 표준).
