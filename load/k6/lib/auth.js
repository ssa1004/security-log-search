// mock JWT helper — k6 시나리오에서 Authorization 헤더 + tenant claim 매핑.
//
// security-log-search 는 SecurityConfig 가 prod profile 에서만 JWT Resource Server 를
// 활성화하고, dev / local 에서는 anyRequest().permitAll() 로 통과시킨다. dev 경로에서는
// OperatorContextResolver 의 fallback 이 항상 tenantId=acme 로 떨어지므로, body 의
// tenantId 필드가 실질적인 멀티테넌트 식별 키 — JWT 의 tenant_id claim 은 prod 게이트가
// 켜질 때 본 시나리오를 그대로 재사용할 수 있게 토큰만 갈아끼우는 hook 이다.
//
// 두 가지 경로:
//   1) K6_TOKEN env 가 비어 있으면 빈 헤더 — dev / local 통합 환경에서 그대로 통과.
//   2) K6_TOKEN 이 있으면 Bearer 로 부착 — prod / ingress JWT 게이트가 켜진 환경.
//      multi-tenant-isolation 시나리오는 tenant 별 토큰을 갈아끼울 수 있도록 K6_TOKEN_ACME /
//      K6_TOKEN_GLOBEX env 도 별도로 받는다 (한 토큰이 두 tenant 를 모두 가지면 격리가
//      깨지므로 시나리오의 invariant 가 의미를 잃는다).

import encoding from 'k6/encoding';

const ENV_TOKEN = __ENV.K6_TOKEN || '';
const TOKEN_ACME = __ENV.K6_TOKEN_ACME || '';
const TOKEN_GLOBEX = __ENV.K6_TOKEN_GLOBEX || '';

/**
 * 공통 Authorization 헤더 — 단일 tenant 시나리오용. 토큰이 비어 있으면 빈 객체.
 */
export function authHeader() {
  if (!ENV_TOKEN) return {};
  return { Authorization: `Bearer ${ENV_TOKEN}` };
}

/**
 * tenant 별 Authorization 헤더 — multi-tenant-isolation 시나리오용.
 *
 * dev profile 에서는 토큰이 없어도 body 의 tenantId 가 격리 키로 동작 (controller 가
 * @RequestBody.tenantId 를 그대로 OpenSearch alias 에 매핑). prod 에서는 K6_TOKEN_ACME /
 * K6_TOKEN_GLOBEX 가 각각 다른 tenant_id claim 을 담은 토큰이어야 invariant 가 의미를
 * 갖는다 — 같은 토큰을 두 tenant 에 재사용하면 prod 게이트가 4xx 로 거절한다.
 */
export function tenantAuthHeader(tenantId) {
  const token = pickTenantToken(tenantId);
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

function pickTenantToken(tenantId) {
  if (tenantId === 'acme' && TOKEN_ACME) return TOKEN_ACME;
  if (tenantId === 'globex' && TOKEN_GLOBEX) return TOKEN_GLOBEX;
  return ENV_TOKEN;
}

/**
 * 토큰 raw 값을 돌려준다 — 진단용.
 */
export function rawToken() {
  return ENV_TOKEN;
}

/**
 * 테스트용 unsigned JWT — prod 의 JwtDecoder 가 reject 하지만 dev / local 에서는 의미.
 * jwt.io 호환 base64url 인코딩. 서명은 sha256 가 k6 stdlib 에 없어 빈 값으로 둔다.
 *
 * @param subject {string} — sub claim
 * @param tenantId {string} — tenant_id claim (OperatorContextResolver 가 강제로 읽는다)
 * @param ttlSeconds {number} — exp 까지의 초
 */
export function unsignedJwt(subject = 'k6-load', tenantId = 'acme', ttlSeconds = 3600) {
  const header = { alg: 'none', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: subject,
    tenant_id: tenantId,
    roles: ['OPERATOR'],
    iat: now,
    exp: now + ttlSeconds,
  };
  const part = (o) => base64url(JSON.stringify(o));
  return `${part(header)}.${part(payload)}.`;
}

function base64url(s) {
  return encoding.b64encode(s, 'rawurl');
}
