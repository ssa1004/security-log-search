package com.example.security.application.service

import com.example.security.application.exception.InsufficientPrivilegeException
import com.example.security.application.exception.TenantNotFoundException
import com.example.security.application.port.`in`.OnboardTenantUseCase
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.out.AuditLogPort
import com.example.security.application.port.out.IndexAdminPort
import com.example.security.application.port.out.TenantRepository
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.common.TenantId
import com.example.security.domain.tenant.Tenant
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** use case 9 — 신규 tenant onboarding. */
@Service
open class OnboardTenantService(
    private val tenants: TenantRepository,
    private val indexAdmin: IndexAdminPort,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : OnboardTenantUseCase {

    @Transactional
    override fun onboard(command: OnboardTenantUseCase.OnboardCommand, operator: OperatorContext): Tenant {
        enforcePlatformAdmin(operator)
        val tenant = Tenant(
            command.tenantId,
            command.displayName,
            command.retention,
            command.hotRetention,
            command.piiPolicy,
            clock.instant(),
            true,
        )
        val saved = tenants.save(tenant)
        indexAdmin.provisionForTenant(saved)
        indexAdmin.applyIlmPolicy(saved)
        indexAdmin.provisionClickHouseRowPolicy(saved)

        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                saved.tenantId,
                clock.instant(),
                operator.subject,
                operator.roles.joinToString(",") { it.name },
                AuditEntry.AuditAction.TENANT_ONBOARDED,
                "tenant",
                saved.tenantId.value,
                operator.sourceIp,
                mapOf(
                    "displayName" to saved.displayName,
                    "retention" to saved.retention.toString(),
                    "hotRetention" to saved.hotRetention.toString(),
                    "piiPolicy" to saved.piiPolicy.name,
                ),
            )
        )

        return saved
    }

    @Transactional
    override fun deactivate(tenantId: TenantId, operator: OperatorContext) {
        enforcePlatformAdmin(operator)
        val existing = tenants.findById(tenantId).orElseThrow { TenantNotFoundException(tenantId) }
        val deactivated = Tenant(
            existing.tenantId,
            existing.displayName,
            existing.retention,
            existing.hotRetention,
            existing.piiPolicy,
            existing.onboardedAt,
            false,
        )
        tenants.save(deactivated)
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                tenantId,
                clock.instant(),
                operator.subject,
                operator.roles.joinToString(",") { it.name },
                AuditEntry.AuditAction.TENANT_DEACTIVATED,
                "tenant",
                tenantId.value,
                operator.sourceIp,
                mapOf("active" to "false"),
            )
        )
    }

    /**
     * tenant 라이프사이클 (onboard / deactivate) 은 플랫폼 운영자만 — 한 tenant 의 ADMIN 이
     * 다른 tenant 를 생성 / 비활성화하는 것을 차단.
     */
    private fun enforcePlatformAdmin(operator: OperatorContext) {
        if (!operator.canQueryOtherTenant()) {
            throw InsufficientPrivilegeException("PLATFORM_ADMIN")
        }
    }
}
