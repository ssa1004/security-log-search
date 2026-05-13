// Sigma 룰 publish → 트리거 이벤트 → alert.fired 까지의 end-to-end latency.
//
// 검증 흐름 (e2e — Flink streaming 경로):
//   1) setup — Sigma YAML 1건 import (POST /api/v1/sigma-rules). brute-force 패턴
//      (5분 안 같은 source.ip 에서 5회 인증 실패). ImportSigmaRuleUseCase 가 AlertRule
//      을 만들어 alert_rules 테이블에 INSERT → Kafka 의 alert-rules.changed 토픽에 publish.
//      Flink job 의 broadcast state 가 이를 받아 즉시 평가에 반영.
//   2) default — 한 iteration 에서 같은 source.ip + outcome=failure 인 인증 이벤트 5건을
//      연달아 publish 한 뒤, 일정 시간 polling 으로 GET /api/v1/alerts 를 호출해 본 룰의
//      alert 가 fired 됐는지 확인.
//   3) 발견 시각 - 마지막 publish 시각 = alert_fired_latency_ms 로 기록.
//
// Flink streaming 의 정상 동작 기준 (ADR-0008): 룰 평가 + Kafka publish + Spring consumer
// INSERT 까지의 총 latency 가 평소 1~3s. 본 시나리오의 임계는 5s (p95) — 부하 / GC 등의
// 변동을 감안.
//
// thresholds:
//   - alert_fired_latency_ms p95 < 5000 — Flink → Kafka → DB 까지의 end-to-end. 5s 를
//     넘기면 streaming pipeline 어딘가에 lag.
//   - alert_fired_count > 0 — 한 건도 fired 되지 않으면 시나리오 자체가 무효 (룰이 적용
//     안 됐거나 broadcast state 가 안 보임).
//   - sigma_import_failure == 0 — setup 단계 실패는 모든 후속 측정의 전제 깨짐.
//   - http_req_failed rate < 5%

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  BASE_URL,
  buildEventPayload,
  newIdempotencyKey,
} from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const firedLatency = new Trend('alert_fired_latency_ms', true);
const firedCount = new Counter('alert_fired_count');
const importFailure = new Counter('sigma_import_failure');
const notFiredWithinDeadline = new Counter('alert_not_fired_within_deadline');

// brute-force 트리거에 사용할 IP — 한 iteration 안에서만 의미. VU 마다 다른 IP 를 써 룰
// 평가가 VU 간 섞이지 않도록 (Flink keyed state 가 source.ip 로 partition).
function ipForVu(vuId, iter) {
  return `203.0.113.${(vuId * 7 + iter) % 250 + 1}`;
}

// brute-force Sigma 룰 — sample-sigma-rules/auth_failed_brute_force.yml 의 단순화 버전.
// 5분 안 같은 source.ip 5회 logon failure → MEDIUM alert.
const SIGMA_YAML = `title: k6 alert-rule-eval brute-force
id: 00000000-0000-4000-8000-${Date.now().toString().padStart(12, '0')}
status: test
description: k6 load test 용 brute-force 룰
logsource:
  category: authentication
detection:
  selection:
    event.category: authentication
    event.action: logon
    event.outcome: failure
  condition: selection
  timeframe: 5m
fields:
  - source.ip
level: medium
`;

const TENANT = __ENV.K6_ALERT_TENANT || 'acme';

// alert polling 한도 — 한 iteration 당 polling 최대 시간 (ms). 5s 임계 + 1s 마진.
const POLL_DEADLINE_MS = 6000;
const POLL_INTERVAL_MS = 500;

export const options = {
  scenarios: {
    alert_cycle: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 2 },
        { duration: '60s', target: 2 },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    alert_fired_latency_ms: ['p(95)<5000'],
    alert_fired_count: ['count>0'],
    sigma_import_failure: ['count==0'],
  },
};

/**
 * setup — 시나리오 시작 전 한 번만 Sigma 룰 import. 이미 같은 title 이 있으면
 * overwriteByTitle=true 로 덮어쓴다 (sigma_id 가 timestamp 포함이라 본 run 안에서는
 * unique 하지만, 이전 run 의 잔재가 있어도 안전).
 */
export function setup() {
  const body = JSON.stringify({
    tenantId: TENANT,
    yaml: SIGMA_YAML,
    overwriteByTitle: true,
  });
  const res = http.post(`${BASE_URL}/api/v1/sigma-rules`, body, {
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    tags: { name: 'sigma-import' },
  });
  const ok = res.status >= 200 && res.status < 300;
  if (!ok) {
    // setup 실패 시 threshold 가 잡지만, default 함수가 실행되면 importFailure 가 누적되도록.
    return { importedOk: false, status: res.status, body: res.body };
  }
  // Flink broadcast state 가 새 룰을 받아 평가에 반영하기까지의 propagation. Kafka
  // alert-rules.changed → Flink consumer poll → broadcast state update. 2s 안전 마진.
  sleep(2);
  return { importedOk: true };
}

function publishTriggerEvent(ip, idx) {
  const body = JSON.stringify({
    tenantId: TENANT,
    source: 'firewall',
    schema: 'ecs',
    payload: buildEventPayload({
      sourceIp: ip,
      action: 'logon',
      outcome: 'failure',
      category: 'authentication',
      ecsSeverity: 70,
      message: `k6 brute-force trigger ${idx}`,
    }),
  });
  return http.post(`${BASE_URL}/api/v1/events`, body, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': newIdempotencyKey('alert-eval'),
      ...authHeader(),
    },
    tags: { name: 'alert-eval-publish' },
  });
}

function listAlerts() {
  // groupKey 가 source.ip 라 페이지 끝까지 보지 않아도 최신 50건이면 본 iteration 의 alert
  // 가 잡힌다 (k6 RPS 가 낮고 alert 가 5분에 한 번이라 page 안에 충분히 들어옴).
  return http.get(`${BASE_URL}/api/v1/alerts?tenantId=${TENANT}&size=50`, {
    headers: authHeader(),
    tags: { name: 'alert-eval-list' },
  });
}

function findAlertForIp(res, ip) {
  try {
    const json = JSON.parse(res.body || '{}');
    const alerts = json.alerts || [];
    // groupKey 가 source.ip 그대로. 일부 구현은 prefix 가 붙을 수 있어 includes 로 매칭.
    for (const a of alerts) {
      if (a.groupKey && a.groupKey.includes(ip)) return a;
    }
  } catch (_) {
    // ignore
  }
  return null;
}

export default function (data) {
  if (!data || data.importedOk !== true) {
    importFailure.add(1);
    sleep(2);
    return;
  }

  const ip = ipForVu(__VU, __ITER);

  // 1) 5회 인증 실패 (threshold) 를 한 번에 발사. 마지막 publish 시각이 alert 의 trigger
  //    시각 — Flink window 안 5번째 event 가 매치를 발화.
  let lastPublishAt = 0;
  for (let i = 1; i <= 5; i++) {
    publishTriggerEvent(ip, i);
    lastPublishAt = Date.now();
  }

  // 2) GET /api/v1/alerts polling — POLL_DEADLINE_MS 안에 본 ip 의 alert 가 나타날 때까지.
  const deadline = lastPublishAt + POLL_DEADLINE_MS;
  let alert = null;
  while (Date.now() < deadline) {
    const res = listAlerts();
    const ok = check(res, { 'alerts 200': (r) => r.status === 200 });
    if (ok) {
      alert = findAlertForIp(res, ip);
      if (alert) break;
    }
    sleep(POLL_INTERVAL_MS / 1000);
  }

  if (alert) {
    firedCount.add(1);
    // alert.firedAt 은 Flink 가 매치를 본 순간 (ISO-8601). DB 의 INSERT 시점이 아니라
    // streaming 의 처리 시각 → 진짜 end-to-end 측정에 더 가깝다.
    const firedAtMs = alert.firedAt ? Date.parse(alert.firedAt) : Date.now();
    const latency = firedAtMs - lastPublishAt;
    // 음수 (clock skew) 또는 비현실적 큰 값은 무시 — 잘못된 sample 이 분포를 망친다.
    if (latency >= 0 && latency < 60_000) {
      firedLatency.add(latency);
    }
  } else {
    notFiredWithinDeadline.add(1);
  }

  // window 5m 이라 한 iteration 끝나면 키 (source.ip) 가 cooldown 으로 다시 같은 룰에
  // 재발화하기까지의 간격을 줘야 다음 iteration 도 의미가 있다. 짧게라도 sleep.
  sleep(2);
}
