# ADR-0014 source 매퍼 분리 — CloudTrail / K8s audit → ECS 정규화

- 상태: Accepted
- 날짜: 2026-05-09

## 맥락

ADR-0002 에서 ECS 를 1차 도메인 모델로 두고 OCSF source 는 변환해서 받기로 했다. 그러나
실제 운영에서는 raw 포맷이 ECS / OCSF 어느 표준도 따르지 않는 source 가 다수 존재한다.

대표 예:

- **AWS CloudTrail** — AWS 계정의 모든 API 활동을 JSON 으로 기록. 자체 필드 (`eventName`,
  `userIdentity.arn`, `sourceIPAddress`, `awsRegion`, `requestParameters`) 사용. ECS / OCSF
  어느 쪽도 native 가 아님.
- **Kubernetes audit log** — kube-apiserver 의 audit policy 로 생성. 자체 필드 (`verb`,
  `objectRef.namespace`, `responseStatus.code`, `sourceIPs`). ECS 에 매핑 정의 부재.
- (후속 후보) syslog (RFC 5424), Windows Event Log XML, GCP Cloud Audit Logs.

이 source 들은 각 source 의 raw 포맷 → ECS 매퍼를 거쳐야 SIEM 으로 들어올 수 있다.
"클라이언트가 ECS 로 보낸다" 는 가정은 현실에서 깨진다 (수집 agent 가 ECS-aware 한 경우는
Filebeat 등 일부에 한정).

## 검토한 대안

1. **클라이언트 측 변환** — 수집 agent (예: Fluentd, Vector) 에서 ECS 로 변환해서 본
   시스템에 넣는다. 매퍼가 시스템 바깥에 있다.
2. **단일 거대 매퍼** — `EcsNormalizer` 가 알려진 모든 source 의 raw 포맷을 분기 처리.
3. **source 별 매퍼 분리 (본 채택안)** — `EventNormalizer` 인터페이스 구현체를 source 별로
   별도 클래스로 분리. `RoutingNormalizer` 가 schema 힌트로 라우팅.

## 결정

대안 3 채택. 새 패키지 `com.example.security.domain.mapping.source` 에 source 별 매퍼를 둔다.

```
mapping/
  EventNormalizer.kt          ← 인터페이스
  EcsNormalizer.kt            ← ECS dotted notation 입력
  OcsfNormalizer.kt           ← OCSF → ECS
  RoutingNormalizer.kt        ← schema 힌트로 라우팅
  source/
    CloudTrailToEcsMapper.kt
    K8sAuditToEcsMapper.kt
```

### schema 힌트 = source 식별자

`RawEvent.schema` 가 `aws-cloudtrail` / `k8s-audit` 일 때 해당 매퍼가 호출된다. REST 측에서는
endpoint 별로 schema 를 자동 결정한다 (수집 agent 가 schema 필드를 따로 안 보내도 됨):

- `POST /api/v1/events/cloudtrail` → schema=`aws-cloudtrail`
- `POST /api/v1/events/k8s-audit`  → schema=`k8s-audit`

기존 `POST /api/v1/events` 는 그대로 유지 — 클라이언트가 ECS / OCSF 인 경우 사용.

### CloudTrail → ECS 매핑

| CloudTrail | ECS |
|---|---|
| `eventTime` | `@timestamp` |
| `eventName` | `event.action` |
| `eventSource` | `event.provider` (labels) |
| `userIdentity.arn` | `user.id` (labels) |
| `userIdentity.userName` | `user.name` (없으면 sessionIssuer / principalId fallback) |
| `sourceIPAddress` | `source.ip` |
| `awsRegion` | `cloud.region` (labels) |
| `recipientAccountId` | `cloud.account.id` (labels) |
| `requestParameters` | `event.original.request_parameters` (labels) |
| `errorCode` 존재 | `event.outcome=failure`, severity HIGH (auth 일 때) |
| `errorCode` 미존재 | `event.outcome=success` |

`event.category` 는 `eventName` 패턴으로 분기:

- `ConsoleLogin` / `AssumeRole` / `*Login*` → `authentication`
- `Create*` / `Update*` / `Delete*` / `Put*` → `configuration`
- `*iam.amazonaws.com` source → `iam`
- 그 외 → `api`

### K8s audit → ECS 매핑

| K8s audit | ECS |
|---|---|
| `requestReceivedTimestamp` (없으면 `stageTimestamp`) | `@timestamp` |
| `verb` | `event.action` |
| `user.username` | `user.name` |
| `user.uid` | `user.id` (labels) |
| `user.groups` | `user.groups` (labels, 콤마 구분) |
| `sourceIPs[0]` | `source.ip` |
| `objectRef.namespace` | `kubernetes.namespace` (labels) |
| `objectRef.resource` / `subresource` / `name` | `kubernetes.*` (labels) |
| `responseStatus.code` 2xx/3xx | `event.outcome=success` |
| `responseStatus.code` 4xx/5xx | `event.outcome=failure` |

`event.category`:

- `requestURI` 가 `tokenreviews` / `subjectaccessreviews` 포함 → `authentication`
- `verb` ∈ {create, update, patch, delete, deletecollection} → `configuration`
- 그 외 → `api`

severity 는 outcome + verb + HTTP code 조합으로 결정 (401/403 → HIGH, 5xx → MEDIUM, delete 성공 → MEDIUM).

### 미지원 필드 처리 — labels 보존

매퍼가 ECS 정식 필드로 매핑하지 않은 raw 키는 `labels` 에 원본 값을 보존한다 (ECS spec 에서
"vendor-specific 필드는 labels 에" 권고). 이렇게 하면:

- 검색 / 운영 UI 에서 labels 를 그대로 노출 가능 (key-value 검색).
- 후속 enrichment 단계 (e.g. CloudTrail `requestParameters` 를 별도 컬럼으로 펼치기) 에서
  원본을 잃지 않음.

복잡한 nested 구조 (예: CloudTrail `requestParameters`, K8s `requestObject`) 는 JSON 문자열
그대로 `labels.event.original.*` 에 넣는다. 검색은 가능하되 구조 query 는 후속 enrichment 후.

## 결과

- 새 source 추가 = 새 `EventNormalizer` 구현체 1개 + `RoutingNormalizer.register(...)` + 단위
  테스트.
- AWS / K8s native 수집 agent (Fluent Bit / vector / kube-apiserver 직접) 의 raw payload 를
  바로 받음. 클라이언트 측 변환 부담 0.
- ECS 1차 모델은 그대로 유지 — 검색 / 룰 / 대시보드 / OpenSearch 매핑 변경 0.
- 매퍼는 도메인 layer (`security-domain`) 에 둬서 외부 framework 의존성 0. 단위 테스트가
  framework 없이 빠르게 돌아감.

## 단점

- 매퍼 별로 주관적인 카테고리 / severity 결정 규칙이 들어간다. CloudTrail 의 `eventName`
  prefix 만으로 카테고리를 정하는 식은 정확도에 한계. 후속 enrichment 단계가 필요할 수 있다.
- 새 source 의 raw 포맷이 변하면 (예: K8s audit v1 → v2) 매퍼 갱신 필요. 회귀 테스트 필수.
- `requestParameters` 같이 큰 nested 객체는 labels 에 문자열로 들어가서 OpenSearch 의 mapping
  explosion 위험은 줄지만, 구조 검색은 불가능 (별도 enrichment 필요).

## 다시 검토할 시점

- syslog (RFC 5424) / Windows Event Log XML / GCP Cloud Audit Logs 추가 시점 — 같은 패턴으로
  매퍼 추가가 잘 되는지 검증.
- `requestParameters` 등 nested object 의 enrichment 가 필요해진 시점 — 별도 enrichment
  pipeline (Flink stage 또는 ingest pipeline) 도입.
- ECS 가 CloudTrail / K8s audit 매핑 spec 을 정식 발표하면 본 매퍼의 카테고리 결정 규칙을
  spec 에 맞춰 갱신.

## 용어 풀이 (쉽게)

- **AWS CloudTrail** — AWS 계정 안에서 일어난 모든 작업(누가 어떤 API를 호출했나)을 자동으로 적어 주는 AWS의 활동 기록. 'AWS 안의 CCTV 영수증'인데 양식이 AWS 고유라 ECS로 번역이 필요하다.
- **K8s audit log(쿠버네티스 감사 로그)** — 쿠버네티스 관리 서버(kube-apiserver)에 들어온 요청(누가 무슨 자원을 만들고 지웠나)을 남긴 기록. 역시 양식이 고유해 ECS로 번역해 받는다.
- **source별 매퍼 분리(EventNormalizer / RoutingNormalizer)** — source마다 양식이 달라, source별 번역기를 따로 두고 '이건 CloudTrail용, 저건 K8s용'으로 골라 보내는(라우팅) 구조. 새 source는 번역기 한 개만 더하면 된다.
- **ARN** — AWS 자원·사용자를 가리키는 고유 주소 문자열. '이 작업을 한 사람·역할이 누구인가'를 콕 집는 식별자다.
- **labels(레이블) 보존** — 정식 칸에 딱 안 맞는 원본 값들을 버리지 않고 '꼬리표(labels)' 칸에 그대로 담아 두는 것. 나중에 펼쳐 쓸 수 있게 원본을 잃지 않으려는 안전장치.
- **fallback(대체값)** — 원하던 칸(예: 사용자명)이 비어 있을 때 다른 칸에서 그럴듯한 값을 대신 끌어다 쓰는 것. 빈칸으로 두지 않고 차선책을 채워 넣는 셈.
- **enrichment(보강)** — 들어온 로그에 부족한 정보를 나중 단계에서 덧붙여 더 쓸모 있게 만드는 것. 큰 nested 값을 펼쳐 별도 칸으로 정리하는 후속 가공이 그 예.
- **mapping explosion(매핑 폭발)** — 검색 엔진이 들어오는 키 종류가 너무 많아져 색인 정의가 걷잡을 수 없이 불어나는 문제. 큰 nested 값을 문자열로 통째 넣어 이 폭발을 줄인다.
- **nested 객체(중첩 구조)** — 값 안에 값이 또 들어 있는 계단식 구조(요청 파라미터 묶음 등). 그대로 펼치면 칸이 폭증해, 일단 JSON 문자열로 담아 둔다.

## 참고

- [AWS Docs — CloudTrail event reference](https://docs.aws.amazon.com/awscloudtrail/latest/userguide/cloudtrail-event-reference-record-contents.html)
- [Kubernetes Docs — Audit Log](https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/)
- [Elastic Common Schema 8.x — labels](https://www.elastic.co/guide/en/ecs/current/ecs-base.html#field-labels)
