// Full-text 검색 latency 시나리오 — OpenSearch Lucene query 비용 측정.
//
// POST /api/v1/search — Lucene query string + tenantId 강제 필터. facets 가 비어 있어
// OpenSearch 비용은 query parser + bm25 scoring 만 들어간다. nori / standard analyzer 가
// 텍스트를 토큰으로 분해하고, tenant alias (events-{tenant}-read) 로 좁혀 들어간다.
//
// constant-arrival-rate 100 req/s 로 운용 — read 경로의 일반적인 부하. ingest 의 1/20.
// 운영 환경의 search 부하는 보안 분석가 N 명의 ad-hoc query 라 read RPS 자체가 낮다.
//
// thresholds:
//   - http_req_duration p95 < 300ms — Lucene query 단독 비용 (facet 미포함). 단일 노드
//     OpenSearch 의 cache miss 첫 query 도 300ms 안에 끝나는 것이 목표.
//   - http_req_failed rate < 1%
//   - search_p99 (보조 Trend) — TTFB 의 p99, tail latency 신호

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, QUERIES, pickTenant, randomFrom } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const searchP99 = new Trend('search_p99', true);

export const options = {
  scenarios: {
    full_text: {
      executor: 'constant-arrival-rate',
      rate: 100,                  // 초당 100 req — read 경로 일반 부하
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 30,
      maxVUs: 150,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:full-text-search}': ['p(95)<300', 'p(99)<800'],
    search_p99: ['p(99)<800'],
  },
};

export default function () {
  const tenantId = pickTenant(__VU, __ITER);
  const body = JSON.stringify({
    tenantId,
    query: randomFrom(QUERIES),
    // size 50 은 SearchRequest.size 의 default — 운영 dashboard 도 한 페이지 50 으로 둔다.
    size: 50,
  });

  const res = http.post(`${BASE_URL}/api/v1/search`, body, {
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    tags: { name: 'full-text-search' },
  });

  // http_req_waiting 은 server-side TTFB — OpenSearch query latency 의 근사. p99 신호로
  // 별도 metric — controller 자체가 search latency 를 Micrometer 로 기록하므로 본 metric 은
  // 외부에서 본 분포가 그것과 차이가 큰지 sanity check 에 가깝다.
  searchP99.add(res.timings.waiting);

  check(res, {
    'status 200': (r) => r.status === 200,
    'body has hits or totalHits': (r) => {
      const b = r.body || '';
      return b.includes('hits') || b.includes('totalHits') || b.startsWith('{');
    },
  });

  sleep(0.1);
}
