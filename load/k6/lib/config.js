// 시나리오 공통 설정.
//
// BASE_URL 은 환경변수로 덮어쓸 수 있도록. 기본은 docker-compose 의 노출 포트 8080.

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * tenant pool — SIEM 의 핵심 격리 단위. seed_demo_data.sh 가 만드는 'globex' + default
 * 'acme' 두 개를 round-robin. ingest / search 양쪽이 같은 풀을 사용해 (tenant, source)
 * cardinality 가 OpenSearch alias / ClickHouse partition 으로 어떻게 흩어지는지 본다.
 *
 * 한 시나리오 안에서 한쪽 tenant 만 두드리고 싶을 때는 `K6_TENANTS=acme` 처럼 단일 값 주입.
 */
export const TENANTS = (__ENV.K6_TENANTS || 'acme,globex')
  .split(',')
  .map((s) => s.trim())
  .filter((s) => s.length > 0);

/**
 * VU 인덱스 + iteration 기반 tenant 선택 — 풀을 고르게 분산.
 */
export function pickTenant(vuId, iter) {
  if (TENANTS.length === 0) return 'acme';
  return TENANTS[(vuId + iter) % TENANTS.length];
}

/**
 * 로그 source pool — 방화벽 / EDR / 시스템 / 응용 로그 등 SIEM 의 일반적인 수집 경로.
 * IngestController 가 source 를 metric tag 로 사용하므로 cardinality 가 폭주하지 않게
 * 의도적으로 5개로 묶는다.
 */
export const SOURCES = ['firewall', 'edr', 'syslog', 'cloudtrail', 'k8s-audit'];

/**
 * ECS event.action — schema 정규화 분기를 다양하게 자극.
 */
export const ACTIONS = ['logon', 'process_started', 'network_connection', 'file_modified', 'logout'];

/**
 * ECS event.outcome — 일부 시나리오 (특히 alert-rule-eval) 는 'failure' 만 발사해 룰을 자극.
 */
export const OUTCOMES = ['success', 'failure', 'unknown'];

/**
 * ECS event.category — facet aggregation 의 cardinality 후보.
 */
export const CATEGORIES = ['authentication', 'process', 'network', 'file', 'iam'];

/**
 * Severity 5단계 — domain.common.Severity 와 1:1. event.severity (0~100) 매핑은
 * Severity.fromEcsScore() 가 담당.
 */
export const SEVERITIES = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/**
 * full-text query pool — Lucene query string. ECS field 와 자유 키워드 혼합. 단순한
 * 단일 단어 (term query) 와 field-scoped query 를 섞어 query parser 비용을 다양하게.
 */
export const QUERIES = (__ENV.K6_QUERIES
  || 'failed,brute-force,powershell,port scan,user.name:alice,event.outcome:failure,source.ip:192.168.*,authentication AND failure,error,denied')
  .split(',')
  .map((s) => s.trim())
  .filter((s) => s.length > 0);

/**
 * facet field pool — POST /api/v1/search 의 facets 배열에 들어가는 ECS field. facet 비용
 * 시나리오는 한 쿼리당 3개를 무작위로 묶어 aggregation 비용 분포를 본다.
 */
export const FACET_FIELDS = ['event.action', 'event.category', 'event.outcome', 'source.ip', 'user.name', 'host.name'];

export function randomFrom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/**
 * 한 raw event payload 생성 — IngestController 의 IngestRequest 에 그대로 들어간다.
 * seed_demo_data.sh 의 ECS payload 형태를 그대로 따른다.
 *
 * @param overrides {object} — 일부 필드 강제 (alert-rule-eval 시나리오용)
 */
export function buildEventPayload(overrides = {}) {
  const action = overrides.action || randomFrom(ACTIONS);
  const outcome = overrides.outcome || randomFrom(OUTCOMES);
  const category = overrides.category || randomFrom(CATEGORIES);
  // event.severity 는 ECS 표준의 0~100 정수. Severity enum 으로 5단계 매핑됨.
  const ecsSeverity = overrides.ecsSeverity || (10 + Math.floor(Math.random() * 90));
  // source.ip 는 alert-rule-eval 시나리오가 groupByField 로 사용 — 풀을 좁혀 같은 키로
  // window 안 N 회 누적이 잘 일어나도록.
  const srcIp = overrides.sourceIp
    || `192.168.${1 + Math.floor(Math.random() * 5)}.${10 + Math.floor(Math.random() * 50)}`;
  const user = overrides.userName || `user-${Math.floor(Math.random() * 100)}`;
  return {
    'event.category': category,
    'event.action': action,
    'event.outcome': outcome,
    'event.severity': ecsSeverity,
    'source.ip': srcIp,
    'user.name': user,
    message: overrides.message || `k6-load action=${action} outcome=${outcome}`,
  };
}

/**
 * Idempotency-Key 생성 — RFC4122 v4 random uuid 형태. k6 의 stdlib 에 uuid 가 없어
 * Math.random 으로 hex 조합. 시나리오마다 매번 새 키 — 중복 방지 경로 (Postgres
 * idempotency_keys 테이블 lookup) 의 cache miss 비율을 실제와 비슷하게 흉내낸다.
 */
export function newIdempotencyKey(prefix = 'k6') {
  const hex = (n) => Math.floor((1 + Math.random()) * 16 ** n).toString(16).slice(1);
  return `${prefix}-${hex(8)}-${hex(4)}-4${hex(3)}-${(8 + Math.floor(Math.random() * 4)).toString(16)}${hex(3)}-${hex(12)}`;
}
