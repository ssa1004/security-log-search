package com.example.security.application.service

import com.example.security.application.exception.InsufficientPrivilegeException
import com.example.security.application.exception.TenantMismatchException
import com.example.security.application.exception.TenantNotFoundException
import com.example.security.application.port.`in`.ManageOpenSearchIndexUseCase
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.out.AuditLogPort
import com.example.security.application.port.out.IndexAdminPort
import com.example.security.application.port.out.TenantRepository
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.common.TenantId
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service

/** use case 7 — admin endpoint. */
@Service
open class ManageOpenSearchIndexService(
    private val indexAdmin: IndexAdminPort,
    private val tenants: TenantRepository,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : ManageOpenSearchIndexUseCase {

    override fun createInitialIndex(tenantId: TenantId, operator: OperatorContext) {
        enforceAdmin(operator, tenantId)
        val tenant = tenants.findById(tenantId).orElseThrow { TenantNotFoundException(tenantId) }
        indexAdmin.provisionForTenant(tenant)
        appendAudit(
            tenantId,
            operator,
            AuditEntry.AuditAction.INDEX_CREATED,
            mapOf("tenant" to tenantId.value),
        )
    }

    override fun triggerRollover(
        tenantId: TenantId,
        operator: OperatorContext,
    ): ManageOpenSearchIndexUseCase.RolloverResult {
        enforceAdmin(operator, tenantId)
        val tenant = tenants.findById(tenantId).orElseThrow { TenantNotFoundException(tenantId) }
        val result = indexAdmin.triggerRollover(tenant)
        if (result.rolledOver) {
            appendAudit(
                tenantId,
                operator,
                AuditEntry.AuditAction.INDEX_ROLLOVER,
                mapOf("from" to (result.oldIndex ?: ""), "to" to (result.newIndex ?: "")),
            )
        }
        return result
    }

    override fun applyIlmPolicy(tenantId: TenantId, operator: OperatorContext) {
        enforceAdmin(operator, tenantId)
        val tenant = tenants.findById(tenantId).orElseThrow { TenantNotFoundException(tenantId) }
        indexAdmin.applyIlmPolicy(tenant)
        appendAudit(
            tenantId,
            operator,
            AuditEntry.AuditAction.ILM_POLICY_APPLIED,
            mapOf("policy" to tenant.ilmPolicyName()),
        )
    }

    /**
     * 인덱스 admin 동작 (생성 / rollover / ILM 적용) 은 tenant ADMIN role 이상 + 본 tenant 또는
     * PLATFORM_ADMIN 이 다른 tenant 를 관리할 때만 허용.
     *
     * ISMS-P 2.6 (접근 통제) — function-level authorization 분리. PLATFORM_ADMIN 이 본인 외
     * tenant 인덱스를 관리하면 cross-tenant access 로 audit 에 남긴다.
     */
    private fun enforceAdmin(operator: OperatorContext, tenant: TenantId) {
        if (operator.canQueryOtherTenant()) {
            CrossTenantAccessAudit.recordIfCrossTenant(
                audit, clock, operator, tenant, "index", tenant.value,
            )
            return
        }
        if (!operator.isAdmin()) throw InsufficientPrivilegeException("ADMIN")
        if (operator.tenantId != tenant) throw TenantMismatchException(operator.tenantId, tenant)
    }

    private fun appendAudit(
        tenantId: TenantId,
        operator: OperatorContext,
        action: AuditEntry.AuditAction,
        details: Map<String, String>,
    ) {
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                tenantId,
                clock.instant(),
                operator.subject,
                operator.roles.joinToString(",") { it.name },
                action,
                "index",
                tenantId.value,
                operator.sourceIp,
                details,
            )
        )
    }
}
