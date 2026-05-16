package com.example.security.adapter.`in`.rest.dto

import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Duration

/** POST /api/v1/tenants — 새 tenant 등록. */
@JvmRecord
data class TenantOnboardRequest(
    @field:NotBlank val tenantId: String,
    @field:NotBlank val displayName: String,
    @field:NotNull val retention: Duration,
    @field:NotNull val hotRetention: Duration,
    @field:NotNull val piiPolicy: PiiMaskingPolicy,
)
