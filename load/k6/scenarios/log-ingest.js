// 대용량 로그 수집 throughput 시나리오 — OpenSearch ingestion 경로 부하.
//
// POST /api/v1/events — raw event 를 받아 EventNormalizer 로 ECS / OCSF 변환 후 Kafka
// (events.normalized) 로 publish. consumer 가 OpenSearch bulk API 로 index, ClickHouse
// 로도 동시 적재한다. 본 시나리오는 *수집 throughput* 의 진입점 latency 만 본다 —
// downstream (Kafka → OpenSearch bulk) lag 은 시나리오 안에서 직접 확인할 수 없고
// k6 가 본 건 controller 가 Kafka producer.send() 를 호출하고 ACK 를 받기까지의 비용이다.
//
// constant-arrival-rate 2000 req/s 로 운용 — 단일 노드 OpenSearch / Kafka 의 흔한
// 한도. e2e-tests 의 ingest-throughput-drop runbook 임계 (분당 평소 대비 50%) 의
// 정상치 기준이 약 분당 100k → 초당 1.6k 정도라 2000 은 +25% 헤드룸을 포함한다.
//
// thresholds:
//   - http_req_duration p95 < 100ms — Kafka producer 동기 ACK 까지의 비용. write-heavy
//     라 read 보다 임계를 짧게 — 200ms 이상이면 producer queue saturation / Postgres
//     idempotency lookup 둘 중 한 곳에서 병목.
//   - http_req_failed rate < 1%
//   - ingest_lag_ms p95 (보조) — server-side waiting. controller 가 normalize +
//     idempotency check + Kafka send 까지 모두 한 호출 안에 처리하므로 의미가 있다.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import {
  BASE_URL,
  SOURCES,
  buildEventPayload,
  newIdempotencyKey,
  pickTenant,
  randomFrom,
} from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const ingestLag = new Trend('ingest_lag_ms', true);

export const options = {
  scenarios: {
    ingest: {
      executor: 'constant-arrival-rate',
      rate: 2000,                 // 초당 2000 req — OpenSearch bulk + Kafka producer 한도 근처
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 200,
      maxVUs: 800,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:log-ingest}': ['p(95)<100', 'p(99)<300'],
    ingest_lag_ms: ['p(95)<100'],
  },
};

export default function () {
  const tenantId = pickTenant(__VU, __ITER);
  // schema 는 ecs 고정 — OCSF 도 EventNormalizer 가 지원하지만, 본 시나리오는 단일 경로의
  // throughput 만 본다. OCSF / ECS 양쪽을 섞으면 normalize 분기 비용까지 합쳐져 분포가
  // 두꺼워진다.
  const body = JSON.stringify({
    tenantId,
    source: randomFrom(SOURCES),
    schema: 'ecs',
    payload: buildEventPayload(),
  });

  const res = http.post(`${BASE_URL}/api/v1/events`, body, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': newIdempotencyKey('ingest'),
      ...authHeader(),
    },
    tags: { name: 'log-ingest' },
  });

  // controller 가 normalize + idempotency check + Kafka producer.send().get() 까지 한
  // request 안에 처리한다. http_req_waiting 이 그 전체 비용의 server-side 근사.
  ingestLag.add(res.timings.waiting);

  check(res, {
    'status 202 or 200 (duplicate)': (r) => r.status === 202 || r.status === 200,
    'body has eventId': (r) => (r.body || '').includes('eventId'),
  });

  // sleep 없음 — constant-arrival-rate 가 rate 보장. iteration entry 사이 최소 양보만.
}
