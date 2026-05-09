# Runbook — 알람 폭주 대응

알람: `AlertFireRateSpike` (`sum(rate(security_log_alert_fired_total[5m]))` 가 평소 baseline
대비 10배 이상으로 5분 지속) 또는 SOC 운영자의 수동 escalation.

알람 폭주는 두 가지 다른 상황을 동시에 의미할 수 있다:

- **실제 사고** — 침해가 광범위하게 진행되고 있어 다수 룰이 합리적으로 발화.
- **false positive 폭증** — 새로 import 한 Sigma 룰의 임계값이 너무 낮거나, 정상 트래픽
  변화 (예: 야간 batch 시작) 가 감지 패턴과 겹친 경우.

처음 1분은 둘을 구별하는 데 쓴다. 둘을 구별하기 전에 룰을 끄면 진짜 사고를 놓칠 위험.

## 1. 첫 1분 — 실제 사고 vs false positive 판정

- Grafana — `Security Log Search → Alerts Overview` 대시보드 열기.
- 다음 패널 순서로 보라:
  - **"알람 발생 — 룰 별"** — 한 룰만 발화 폭주인지 / 여러 룰이 동시에 발화하는지.
  - **"severity 분포"** — CRITICAL 이 늘었는지 / LOW-MEDIUM 만 폭주인지.
  - **"groupKey top-N"** — 같은 IP / 같은 user 가 N개 룰을 트리거 중인지.

판정 휴리스틱:

| 패턴 | 추정 | 다음 절 |
|---|---|---|
| 단일 룰만 폭주 + LOW/MEDIUM | **false positive** (룰 임계 잘못) | 3번 |
| 다수 룰 + 같은 groupKey 가 동시에 매치 | **실제 사고** (한 호스트가 광범위) | 2번 |
| 다수 룰 + 다양한 groupKey | **광범위 사고** 또는 **schema drift** (수집 측 변경) | 4번 |

## 2. 실제 사고로 판정된 경우 — *룰을 끄지 말 것*

- SOC 사고 대응 프로세스로 escalation. 본 runbook 의 책무는 **시스템이 알람을 계속 받아낼
  capacity 를 유지** 하는 것까지.
- 알람 처리 (acknowledge) 는 운영자가 `POST /api/v1/alerts/{id}/ack` 로 수동 확인.
- alert volume 이 너무 많아 [flink-job-not-progressing.md](flink-job-not-progressing.md) 의
  sink-side backpressure 가 일어날 수 있음 — Kafka `alerts.fired` partition 증설 검토.
- audit 보존 (ADR-0011) — 사고 기간의 audit_entries 가 정상 ingest 되고 있는지 확인.

## 3. false positive 폭증 — 단일 룰이 원인인 경우

가장 흔한 케이스. 절차:

1. **즉시 mute** — 룰을 비활성화 (delete 가 아니라 disable). 본 시스템에서는 PATCH 가 없으므로
   PUT 으로 enabled=false 만 바꿔 갱신:
   ```bash
   RULE_ID=...
   curl -s "http://api/api/v1/alert-rules/$RULE_ID" | jq '.enabled = false' \
     | curl -s -X PUT "http://api/api/v1/alert-rules/$RULE_ID" \
         -H 'Content-Type: application/json' --data-binary @-
   ```
   보통 5초 안에 Flink broadcast state 가 hot reload 되어 발화가 멈춘다 (ADR-0008).

2. **분석** — 어떤 정상 트래픽이 룰을 만족시켰는지 OpenSearch 검색으로:
   ```
   POST /api/v1/search
   {
     "tenantId": "acme",
     "q": "event.action:logon AND event.outcome:failure",
     "from": "2026-05-09T11:50:00Z",
     "to":   "2026-05-09T12:10:00Z",
     "facets": ["source.ip", "user.name"]
   }
   ```
   - source.ip facet 의 top-1 이 압도적이면 → 단일 호스트의 정상 행동 패턴 (예: 헬스체크
     agent 의 인증 실패가 노이즈).
   - 여러 IP 에 고르게 분포 → baseline 자체 변화 가능 — 룰 임계값 (threshold/window) 재
     설계.

3. **임계값 조정 후 재가동**:
   - threshold 상향 (예: 5건 / 5분 → 10건 / 5분).
   - groupByField 변경 (source.ip → user.name 등으로 노이즈 IP 분리).
   - filterAction / filterOutcome 더 좁게 (logon → logon AND user.name=admin 류는 본
     시스템 룰 DSL 이 단일 필드만 지원 — Sigma re-import 후 매뉴얼 조정 필요).
   - PUT 후 enabled=true 로 다시 켠다. 30분 모니터링.

## 4. 다수 룰 + 다양한 groupKey 폭주 — schema drift 의심

수집 측 (CloudTrail v2 → v3 / K8s audit v1 → v2) 의 schema 변경으로 정상 이벤트가 갑자기
다수 룰을 만족시키는 케이스.

- `security_log_normalize_failures_total` 메트릭이 동시에 증가했는지.
- 최근 1시간 내 source 별 schema 변경 알림 (CloudTrail / GCP audit / K8s audit 의
  release note) 확인.
- 매퍼 (ADR-0014) 갱신 PR 가 필요할 가능성. 그 전까지는 해당 source 의 룰 그룹을 일시 mute.

## 5. 회복 후

- 발화 rate 가 baseline 으로 복귀하면 자동 resolve.
- Postmortem 항목:
  - **사고 / false positive 비율** — 다음 baseline 임계값 산정 input.
  - **룰 mute → unmute 까지 소요 시간** — 운영자 SLO 추적.
  - **Sigma 원본의 unsupported notes** 를 다시 살피고, 변환 한계 때문에 적용된 default
    임계값이 부적절했는지 점검.

## 관련 문서

- ADR-0008 — Alert rule engine + broadcast state hot reload (보통 5초 안 반영)
- ADR-0011 — Audit log append-only (사고 기간 audit 보장)
- ADR-0013 — Sigma 룰 import 한계 / 변환 결과 mappingNotes
- 대시보드: `infrastructure/observability/grafana/dashboards/alerts-overview.json`
- 같이 보면 좋은 runbook: `flink-job-not-progressing.md` (sink backpressure 시)
