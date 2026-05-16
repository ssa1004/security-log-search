package com.example.security.application.service

import com.example.security.application.exception.AlertNotFoundException
import com.example.security.application.exception.TenantMismatchException
import com.example.security.application.port.`in`.ListAlertsUseCase
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.out.AlertRepository
import com.example.security.application.port.out.AuditLogPort
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.Alert
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** use case 6 — 발화된 알람 timeline + 운영자 처리. */
@Service
open class ListAlertsService(
    private val alerts: AlertRepository,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : ListAlertsUseCase {

    override fun list(
        query: ListAlertsUseCase.ListAlertsQuery,
        operator: OperatorContext,
    ): ListAlertsUseCase.Page {
        enforceTenant(operator, query.tenantId)
        val fetched = alerts.query(query)
        val nextCursor: UUID? =
            if (fetched.size == query.size) fetched[fetched.size - 1].alertId else null
        return ListAlertsUseCase.Page(fetched, nextCursor)
    }

    @Transactional
    override fun acknowledge(alertId: UUID, operator: OperatorContext): Alert =
        transition(alertId, operator, { it.acknowledge() }, AuditEntry.AuditAction.ALERT_ACKNOWLEDGED)

    @Transactional
    override fun resolve(alertId: UUID, operator: OperatorContext): Alert =
        transition(alertId, operator, { it.resolve() }, AuditEntry.AuditAction.ALERT_RESOLVED)

    @Transactional
    override fun markFalsePositive(alertId: UUID, operator: OperatorContext): Alert =
        transition(alertId, operator, ::falsePositive, AuditEntry.AuditAction.ALERT_FALSE_POSITIVE)

    private fun falsePositive(a: Alert): Alert =
        Alert(
            a.alertId,
            a.tenantId,
            a.ruleId,
            a.ruleName,
            a.severity,
            a.groupKey,
            a.groupByField,
            a.matchedCount,
            a.windowStart,
            a.windowEnd,
            a.firedAt,
            Alert.AlertStatus.FALSE_POSITIVE,
            a.triggeringEventIds,
            a.message,
        )

    private fun transition(
        alertId: UUID,
        operator: OperatorContext,
        transition: (Alert) -> Alert,
        action: AuditEntry.AuditAction,
    ): Alert {
        val existing = alerts.findById(alertId).orElseThrow { AlertNotFoundException(alertId) }
        enforceTenant(operator, existing.tenantId)
        val updated = transition(existing)
        val saved = alerts.save(updated)
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                saved.tenantId,
                clock.instant(),
                operator.subject,
                operator.roles.joinToString(",") { it.name },
                action,
                "alert",
                saved.alertId.toString(),
                operator.sourceIp,
                mapOf("status" to saved.status.name, "rule" to saved.ruleName),
            )
        )
        return saved
    }

    private fun enforceTenant(operator: OperatorContext, tenant: TenantId) {
        if (!operator.canQueryOtherTenant() && operator.tenantId != tenant) {
            throw TenantMismatchException(operator.tenantId, tenant)
        }
        CrossTenantAccessAudit.recordIfCrossTenant(
            audit, clock, operator, tenant, "alert", tenant.value,
        )
    }
}
