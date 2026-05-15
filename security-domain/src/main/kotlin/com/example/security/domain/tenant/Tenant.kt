package com.example.security.domain.tenant

import com.example.security.domain.common.TenantId
import java.time.Duration
import java.time.Instant

/**
 * 테넌트 — 본 시스템의 격리 단위.
 *
 * onboarding 시 OpenSearch alias / ClickHouse Row Policy / 인덱스 템플릿이 자동 wiring 된다.
 * 테넌트마다 보존 기간 / hot tier 일수 등을 다르게 설정 가능.
 */
@JvmRecord
data class Tenant(
    val tenantId: TenantId,
    val displayName: String,
    /** 데이터 보존 기간 — 이 기간 지난 인덱스는 ILM 이 삭제. ISMS-P 권고 최소 1년. */
    val retention: Duration,
    /** hot tier 유지 기간 — SSD 노드. 운영 트래픽이 많으면 늘림. */
    val hotRetention: Duration,
    /** PII 마스킹 정책 — 운영자 role 별 view 적용 여부. */
    val piiPolicy: PiiMaskingPolicy,
    val onboardedAt: Instant,
    val active: Boolean,
) {

    init {
        require(retention >= Duration.ofDays(365)) {
            "ISMS-P 권고 — 보안 로그 보존 최소 1년: $retention"
        }
        require(hotRetention <= retention) { "hot retention 은 전체 retention 이하" }
    }

    /** OpenSearch read alias. */
    fun readAlias(): String = "events-${tenantId.value}-read"

    /** OpenSearch write alias. */
    fun writeAlias(): String = "events-${tenantId.value}-write"

    /** OpenSearch ILM policy 이름. */
    fun ilmPolicyName(): String = "ilm-events-${tenantId.value}"

    enum class PiiMaskingPolicy {
        /** 마스킹 안 함 (개발용). */
        NONE,

        /** IP 주소 마스킹 (마지막 옥텟). */
        IP_ONLY,

        /** IP + 사용자명 + 이메일 마스킹. */
        STRICT,
    }
}
