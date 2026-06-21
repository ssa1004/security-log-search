<!--
PR 제목은 Conventional Commits 규칙을 따릅니다 (예: feat:, fix:, ci:, build:, docs:).
CONTRIBUTING.md 의 commit / 브랜치 규칙을 참고하세요.
-->

## 변경 요약

<!-- 무엇을, 왜 바꿨는지 1~3줄. -->

## 변경 유형

- [ ] feat — 기능 추가
- [ ] fix — 버그 수정
- [ ] ci / build — 파이프라인 / 빌드 / 컨테이너
- [ ] docs — 문서
- [ ] refactor / chore / test — 기타

## 체크리스트

- [ ] 로컬에서 `./gradlew test` 통과
- [ ] 관련 문서 (README / ADR / runbook) 업데이트
- [ ] Helm 변경 시: `helm lint` + `helm template | kubeconform` 통과
- [ ] Prometheus 룰 변경 시: `promtool check rules` + `promtool test rules` 통과
- [ ] Dockerfile 변경 시: `hadolint` clean
- [ ] 워크플로 변경 시: `actionlint` 통과
- [ ] breaking change 없음 (있다면 아래 명시)

## 보안 / 운영 영향

<!--
인증/인가, 감사 로그, 멀티테넌트 격리, 비밀 처리, RBAC/NetworkPolicy,
securityContext 등에 영향이 있으면 기술하세요. 없으면 "없음".
-->

## 검증 방법

<!-- 리뷰어가 재현할 수 있는 명령 / 단계. -->

## 관련 이슈

<!-- Closes #123 형태. -->
