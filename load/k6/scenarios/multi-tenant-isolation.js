// Multi-tenant 격리 회귀 가드 시나리오 — 보안 invariant 검증.
//
// 검증 대상: ADR-0007 의 4-layer 격리. acme tenant 로 publish 한 raw event 가 globex
// tenant 의 검색에서 **절대로** 보이면 안 된다. controller / use case / OpenSearch alias
// (events-{tenant}-*) / ClickHouse partition 의 네 단계 중 한 곳이라도 tenant 검증을
// 빠뜨리면 leak 이 발생. 본 시나리오는 leak 이 0건임을 invariant 로 강제한다.
//
// 시나리오 흐름:
//   1) acme 토큰으로 표식이 들어간 event N 건 publish (Idempotency-Key 로 dedupe). payload
//      에 source.ip 와 message 에 시나리오 표식 (`k6-isolation-{run_id}`) 을 박는다.
//   2) Kafka → OpenSearch index 까지의 propagation 을 위해 짧게 대기 (3s).
//   3) globex 토큰으로 동일 표식을 search — totalHits == 0 여야 한다.
//   4) sanity check 로 acme 토큰으로 같은 표식을 search — totalHits > 0 이어야 한다
//      (= 시나리오 자체가 publish 에 성공했다는 보장).
//
// 토큰이 dev 환경처럼 비어 있으면 본 시나리오의 invariant 가 무의미 (OperatorContextResolver
// 가 fallback 으로 모든 요청을 acme 로 보냄). 그래도 controller body 의 tenantId 필드는
// 그대로 검증 — adapter-out 의 OpenSearch alias 가 body.tenantId 로 분기되므로 leak 자체는
// dev 에서도 잡힌다. prod 게이트가 켜진 환경에서는 K6_TOKEN_ACME / K6_TOKEN_GLOBEX 가 각각
// 다른 tenant_id claim 을 담은 토큰이어야 의미가 있다.
//
// thresholds (invariant 차단):
//   - tenant_leak_count == 0 — globex 가 acme 데이터를 한 건이라도 보면 즉시 실패.
//   - tenant_sanity_failure == 0 — 표식 자체가 publish 에 실패하면 시나리오 무효.
//   - http_req_failed rate < 5% — admin / write 권한 4xx 가능성 감안.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, buildEventPayload, newIdempotencyKey } from '../lib/config.js';
import { tenantAuthHeader } from '../lib/auth.js';

const tenantLeak = new Counter('tenant_leak_count');
const sanityFailure = new Counter('tenant_sanity_failure');
const isolationRuns = new Counter('tenant_isolation_runs');

// 한 run 안에서 모든 VU 가 공유하는 표식 — message 와 source.ip 둘 다에 박아 query 가
// 어떤 field 를 잡든 검출되도록.
const RUN_TAG = `k6-isolation-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
const TAGGED_IP = '203.0.113.99';  // RFC5737 TEST-NET-3, 실제 트래픽과 충돌 안 함.

export const options = {
  scenarios: {
    isolation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 3 },
        { duration: '30s', target: 3 },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    tenant_leak_count: ['count==0'],
    tenant_sanity_failure: ['count==0'],
    http_req_failed: ['rate<0.05'],
  },
};

function publishToAcme(idem) {
  const body = JSON.stringify({
    tenantId: 'acme',
    source: 'firewall',
    schema: 'ecs',
    payload: buildEventPayload({
      sourceIp: TAGGED_IP,
      message: `${RUN_TAG} payload`,
      action: 'logon',
      outcome: 'failure',
    }),
  });
  return http.post(`${BASE_URL}/api/v1/events`, body, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idem,
      ...tenantAuthHeader('acme'),
    },
    tags: { name: 'isolation-publish-acme' },
  });
}

function searchAs(tenantId) {
  // RUN_TAG 가 message 안에 들어가 있어 free-text query 로 정확히 잡힌다. source.ip 로도
  // 한 번 더 검증할 수 있지만, 한 query 만 잘 잡혀도 leak 은 invariant 위반.
  const body = JSON.stringify({
    tenantId,
    query: RUN_TAG,
    size: 50,
  });
  return http.post(`${BASE_URL}/api/v1/search`, body, {
    headers: { 'Content-Type': 'application/json', ...tenantAuthHeader(tenantId) },
    tags: { name: `isolation-search-${tenantId}` },
  });
}

function totalHitsOf(res) {
  try {
    const json = JSON.parse(res.body || '{}');
    if (typeof json.totalHits === 'number') return json.totalHits;
    if (Array.isArray(json.hits)) return json.hits.length;
    return 0;
  } catch (_) {
    return -1;
  }
}

export default function () {
  // 1) acme 로 표식이 들어간 event 3 건 publish.
  for (let i = 0; i < 3; i++) {
    const pub = publishToAcme(newIdempotencyKey(`isolation-${__VU}-${__ITER}-${i}`));
    check(pub, {
      'publish 202 or 200': (r) => r.status === 202 || r.status === 200,
    });
  }

  // 2) Kafka → OpenSearch index propagation 대기 — 통합 환경에서 보통 1~2s, 안전 마진.
  //    refresh interval 이 1s 인 OpenSearch index 가 일반적이라 3s 면 충분히 visible.
  sleep(3);

  // 3) globex 로 검색 — invariant: 표식이 보이면 안 된다.
  const globexRes = searchAs('globex');
  check(globexRes, { 'globex search 200': (r) => r.status === 200 });
  const globexHits = totalHitsOf(globexRes);
  if (globexHits > 0) {
    // tenant 격리 깨짐 — invariant violation.
    tenantLeak.add(globexHits);
  }

  // 4) sanity — acme 로 같은 검색이 가능해야 (publish 자체가 성공했다는 증거).
  const acmeRes = searchAs('acme');
  check(acmeRes, { 'acme search 200': (r) => r.status === 200 });
  const acmeHits = totalHitsOf(acmeRes);
  if (acmeHits <= 0) {
    // publish 가 실패했거나 index 가 안 보이는 상황 — 본 run 의 invariant 가 무의미해진다.
    // ingestion 경로 자체가 깨진 신호이기도 하므로 별도 counter 로 추적.
    sanityFailure.add(1);
  }

  isolationRuns.add(1);

  sleep(1);
}
