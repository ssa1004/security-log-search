package com.example.security.application.port.`in`

import com.example.security.domain.common.TenantId
import com.example.security.domain.tenant.Tenant
import java.time.Duration

/**
 * use case 9 — 신규 tenant onboarding.
 *
 * 등록 시 다음을 자동 wiring:
 *
 *  - OpenSearch — events-{tenant}-write / -read alias + 초기 인덱스 + ILM 정책
 *  - ClickHouse — Row Policy (WHERE tenant_id = currentSetting('tenant_id'))
 *  - Postgres — tenants 테이블 INSERT
 *  - audit_entries — TENANT_ONBOARDED 기록
 */
interface OnboardTenantUseCase {

    fun onboard(command: OnboardCommand, operator: OperatorContext): Tenant

    fun deactivate(tenantId: TenantId, operator: OperatorContext)

    @JvmRecord
    data class OnboardCommand(
        val tenantId: TenantId,
        val displayName: String,
        val retention: Duration,
        val hotRetention: Duration,
        val piiPolicy: Tenant.PiiMaskingPolicy,
    )
}
