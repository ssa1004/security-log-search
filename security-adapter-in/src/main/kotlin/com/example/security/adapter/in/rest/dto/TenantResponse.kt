package com.example.security.adapter.`in`.rest.dto

import com.example.security.domain.tenant.Tenant
import java.time.Duration
import java.time.Instant

/** GET /api/v1/tenants 응답 — tenant 메타데이터. */
@JvmRecord
data class TenantResponse(
    val tenantId: String,
    val displayName: String,
    val retention: Duration,
    val hotRetention: Duration,
    val piiPolicy: String,
    val onboardedAt: Instant,
    val active: Boolean,
) {

    companion object {
        @JvmStatic
        fun from(t: Tenant): TenantResponse =
            TenantResponse(
                t.tenantId.value,
                t.displayName,
                t.retention,
                t.hotRetention,
                t.piiPolicy.name,
                t.onboardedAt,
                t.active,
            )
    }
}
