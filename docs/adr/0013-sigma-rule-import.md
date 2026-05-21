# ADR-0013: Sigma 룰 import → AlertRule 변환

## 상태
적용

## 배경

SOC (Security Operations Center) 운영자는 외부 보안 인텔리전스를 빠르게 수용해야 한다.
새로운 공격 패턴 (예: APT 그룹의 신규 TTP, 신규 멀웨어 IoC) 이 발견되면 본 시스템에서도
같은 날 탐지 룰이 가동돼야 한다.

이 외부 인텔리전스의 사실상 표준 포맷이 **Sigma** (https://github.com/SigmaHQ/sigma) 다.
Sigma 는 vendor 중립의 SIEM 룰 YAML 포맷으로, 보안 커뮤니티가 수천 개의 공개 룰을 git
repo 로 관리한다. 운영자는 외부 룰 import → 본 시스템 알람으로 변환 → 즉시 가동의
파이프라인이 필요하다.

매번 운영자가 Sigma YAML 을 본 시스템의 룰 DSL 로 손으로 옮기는 것은 비효율이고 오류
가능성도 크다. 자동 변환기가 필요하다.

## 결정

### Sigma YAML → AlertRule 변환기

`ImportSigmaRuleUseCase` — 단일 또는 multi-document YAML 입력 → AlertRule + SigmaRule
영속.

흐름:

1. `SigmaYamlParser` — YAML → `SigmaRule` 도메인 record (id, title, level, detection,
   condition, references, tags, ...)
2. `SigmaToAlertRuleMapper` — Sigma → 자체 `AlertRule` 변환 + `unsupported` (변환 불가 표현)
   목록 생성
3. 같은 Sigma id 가 이미 있으면 `overwrite` 플래그에 따라 skip 또는 같은 alert_rule_id
   유지하고 내용 갱신
4. 변환 결과 + 원본 YAML 을 `sigma_rules` 테이블에 영속 (audit trail)
5. `RULE_CREATED` AuditEntry 발행 — 운영자 / 시각 / 원본 Sigma id 기록

### 필드 매핑 — `SigmaFieldNameMap`

Sigma 의 vendor 별 native 필드 (예: Windows `EventID`, Linux `auid`) → ECS field 매핑.

```
EventID         → event.code
event_id        → event.code
TargetUserName  → user.name
SourceIp        → source.ip
ComputerName    → host.hostname
...
```

ECS 와의 매핑은 Sigma 자체 spec (`sigma-hq/pySigma-backend-elasticsearch` 가 사용하는
mapping 과 정합) 을 그대로 따른다.

### Sigma field modifier

Sigma 는 `EventID|equals: 4625` / `Image|contains: cmd.exe` / `Path|startswith: C:\\` 같은
modifier 를 지원한다. 본 변환기는:

- `equals` / 기본 → 알람 룰의 정확 매칭으로 변환
- `contains` / `startswith` / `endswith` → 인식하지만 룰 DSL 로 변환 시 정확 매칭으로 단순화
  (운영자가 검토 후 수동 보정)
- 그 외 modifier (`re`, `cidr`, `base64offset`, `expand`) → `unsupported` 기록

### condition 표현

본 단계의 룰 DSL 은 단순 selection 만 (예: `condition: selection`) 매핑한다. Sigma 의
복잡 표현은 unsupported 기록:

- `1 of selection*` (논리 합)
- `selection1 and selection2` (논리 곱)
- `selection | count(...) > N` (집계)
- `near` / `timeframe` (시간 윈도우)

이런 룰은 변환기가 기본 골격만 만들고 disabled 로 둔다. 운영자가 별도 Flink CEP /
보강된 룰 DSL 로 수동 구현하도록 `unsupported` 에 명시한다.

### Severity 매핑

Sigma `level` → 자체 `Severity`:
- `informational` / `info` → INFO
- `low` → LOW
- `medium` → MEDIUM (또는 미명시 default)
- `high` → HIGH
- `critical` → CRITICAL

### unsupported 가 있으면 disabled — 운영자 검토 강제

자동 변환 결과를 검토 없이 가동하면 false-positive / 탐지 누락이 생긴다. unsupported 가
비어있지 않은 룰은 `enabled=false` 로 영속 → 운영자가 검토 후 활성화. 안전 default.

### multi-document YAML 지원

Sigma 공개 repo 의 룰들은 `---` 로 여러 문서를 한 파일에 묶기도 한다. 변환기는 multi-doc
YAML 을 한 번에 import 가능 (한 파일 = 한 batch).

## 대안

### 운영자 수동 번역
탈락 — 외부 인텔리전스가 매주 수십~수백 룰씩 추가되는 환경에서 수동 번역은 불가능.

### Sigma 룰을 그대로 native 실행 (별도 Sigma 엔진)
검토 — `pySigma` 같은 Python 엔진을 사이드카로 띄우면 가능하지만:
- JVM 시스템에 Python 의존 추가
- Sigma 엔진 자체의 성능 / 가시성 통제 불편
- 자체 룰 DSL 과 Sigma 엔진 두 시스템이 공존 → 운영 일관성 깨짐

자동 변환 + 자체 룰 엔진 단일화가 더 깔끔.

### 외부 변환 도구 (sigmac CLI) 호출
탈락 — sigmac 가 ES query 로 변환해주지만, 자체 룰 DSL 의 threshold + window + groupBy
표현으로 가는 매핑은 직접 알아야 한다. 결국 자체 변환기 필요.

## 결과

- Sigma 공개 룰 / 사내 인텔리전스를 받는 당일 import 가능 → SOC 대응 속도 향상
- 변환 한계 (unsupported) 가 명시 + 자동 disabled → false-positive / 누락 사고 차단
- 원본 YAML 보존 + audit → "어느 외부 룰이 본 시스템 알람의 출처인가" 추적 가능
- (단점) 단순 selection / equals 만 자동 변환 — 복잡 condition / aggregation / timeframe 은
  운영자 추가 작업 필요. unsupported 에 사유 명시
- (단점) Sigma spec 이 진화 (modifier 추가 / 새 logsource 분류) 시 변환기도 같이 갱신 필요

## 후속

- ADR (예정): Sigma 공개 git repo 자동 polling + diff import (운영자가 매번 import 안 해도 자동 동기화)
- ADR (예정): Sigma `aggregation` / `timeframe` 의 Flink CEP 자동 변환 — 단순 case 만이라도
- ADR (예정): 변환기 회귀 테스트 — Sigma 공개 룰 100개 샘플로 변환 결과 snapshot
