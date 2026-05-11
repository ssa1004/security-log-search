# Security Policy

본 저장소는 포트폴리오 / 학습용 SIEM 백엔드입니다. 운영 환경에서 그대로 사용하는 것을
권장하지 않으며, 발견된 취약점은 가능한 범위에서 패치합니다.

## Supported Versions

| Version | 지원 여부 |
| ------- | ------- |
| `main` (HEAD)  | 지원 |
| 그 외 태그 / 브랜치 | 미지원 |

릴리스 태그는 아직 발급하지 않았습니다 (`0.1.0` SNAPSHOT 단계).

## 취약점 신고

**공개 issue 로 보고하지 마세요.** 다음 경로 중 하나로 비공개 신고를 부탁드립니다:

1. GitHub Security Advisory — [Report a vulnerability](https://github.com/ssa1004/security-log-search/security/advisories/new)
   - 권장. 공개 전 fix 협의가 가능합니다.
2. 메일 — `wittyahn@gmail.com`
   - 제목 prefix `[security-log-search][SECURITY]` 권장.

다음 정보를 포함해 주세요:
- 영향받는 모듈 / 파일 / 커밋 hash
- 재현 절차 (가능한 minimal repro)
- 추정 영향 범위 (RCE / 인증 우회 / 정보 노출 / DoS 등)
- 권장 수정 방향 (있다면)

## 응답 SLA (best-effort)

| 단계 | 목표 |
| ---- | ---- |
| 접수 확인 | 영업일 기준 3일 이내 |
| 1차 평가 결과 회신 | 영업일 기준 7일 이내 |
| 패치 / 완화책 머지 | severity 에 따라 14 ~ 60일 |

본 저장소는 1인 개인 프로젝트입니다. 응답이 늦어질 수 있으며, 보고자에 대한 보상 (bounty)
프로그램은 운영하지 않습니다.

## 범위 (in-scope)

- `security-domain` / `security-application` / `security-adapter-*` / `security-bootstrap`
  / `security-streaming` 의 코드 결함
- `infrastructure/` 의 Dockerfile / Helm chart / docker-compose 의 보안 설정 결함
- CI 파이프라인 (`.github/workflows/`) 의 secret 노출 / 권한 과잉

## 범위 외 (out-of-scope)

- 시연용 mock 서비스 (`infrastructure/docker/mock-auth/`) — 테스트 전용 키쌍 포함
- 기본 설정값으로 띄운 OpenSearch / ClickHouse / Kafka 의 보안 (운영 환경에서는 hardening 필요,
  관련 ADR 참조)
- 외부 SIEM / SOAR 연동 가정 환경의 신뢰 경계

## 이미 알려진 한계

- 본 저장소의 mock auth-service 가 사용하는 RSA key 는 commit 되어 있습니다
  (`infrastructure/docker/mock-auth/`). 이는 의도된 시연용이며 운영 키가 아닙니다.
- 일부 application 설정의 기본값 (예: actuator endpoint 노출) 은 로컬 dev 편의를 위한
  것이며, 운영 시 `application-prod.yml` 또는 Helm values 에서 잠가야 합니다.
