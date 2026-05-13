// Facet aggregation 비용 측정 시나리오 — OpenSearch terms aggregation 의 cardinality
// 비용을 풀 query 와 함께 본다.
//
// POST /api/v1/search — query 위에 facets (terms aggregation N 개) 와 filters (term /
// range) 까지 모두 채운 풀 body. OpenSearch 의 bool query + aggregation tree 가 가장
// 무거운 경우 — 같은 hit set 을 N 번 group-by 하기 때문에 facet 갯수에 비례해 비용 상승.
//
// full-text-search 의 절반 RPS (50 req/s) 로 운용 — query DSL 비용을 격리.
// full-text-search 의 p95 와 본 시나리오의 p95 차이가 facet aggregation 의 *순수* 비용에
// 가깝다 (네트워크 / Kafka / Postgres 변수는 양쪽 동일).
//
// thresholds:
//   - http_req_duration p95 < 500ms — facet 3개 동시 aggregation 의 비용 감안한 느슨한
//     임계. 1s 이상이면 OpenSearch shard 의 fielddata cache miss 가 의심.
//   - http_req_failed rate < 1%
//   - facet_compute_ms p95 < 400ms — server-side waiting 의 단독 임계.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import {
  BASE_URL,
  CATEGORIES,
  FACET_FIELDS,
  QUERIES,
  pickTenant,
  randomFrom,
} from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const facetCompute = new Trend('facet_compute_ms', true);

export const options = {
  scenarios: {
    facet: {
      executor: 'constant-arrival-rate',
      rate: 50,                   // full-text 의 절반 — aggregation 비용 격리
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:facet-aggregation}': ['p(95)<500', 'p(99)<1500'],
    facet_compute_ms: ['p(95)<400'],
  },
};

function pickFacets(n) {
  // 중복 없이 n 개 (작으면 풀에서 직접 sample). FACET_FIELDS 가 6개라 최대 3개까지.
  const pool = [...FACET_FIELDS];
  const out = [];
  while (out.length < n && pool.length > 0) {
    const idx = Math.floor(Math.random() * pool.length);
    out.push(pool[idx]);
    pool.splice(idx, 1);
  }
  return out;
}

export default function () {
  const tenantId = pickTenant(__VU, __ITER);
  const body = JSON.stringify({
    tenantId,
    query: randomFrom(QUERIES),
    // filters 는 SearchRequest.filters: Map<String,String> — equality term 만 (range 는
    // from/to 로 별도). category 한 개로 좁히면 hit set 이 합리적이라 aggregation 비용도
    // 의미 있는 분포로 떨어진다.
    filters: { 'event.category': randomFrom(CATEGORIES) },
    facets: pickFacets(3),
    facetSize: 10,
    size: 20,
  });

  const res = http.post(`${BASE_URL}/api/v1/search`, body, {
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    tags: { name: 'facet-aggregation' },
  });

  // server-side waiting 을 facet 계산 비용의 proxy 로 사용 — full-text-search 의 분포와
  // 비교했을 때의 차이가 aggregation 단독 비용.
  facetCompute.add(res.timings.waiting);

  check(res, {
    'status 200': (r) => r.status === 200,
    'body has facets or totalHits': (r) => {
      const b = r.body || '';
      return b.includes('facets') || b.includes('totalHits');
    },
  });

  sleep(0.2);
}
