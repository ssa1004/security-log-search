package com.example.security.application.service

import com.example.security.application.exception.RuleNotFoundException
import com.example.security.application.exception.TenantMismatchException
import com.example.security.application.port.`in`.DefineAlertRuleUseCase
import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.out.AlertRuleRepository
import com.example.security.application.port.out.AuditLogPort
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** use case 4 — 알람 룰 CRUD. */
@Service
open class DefineAlertRuleService(
    private val repo: AlertRuleRepository,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : DefineAlertRuleUseCase {

    @Transactional
    override fun create(command: DefineAlertRuleUseCase.CreateRuleCommand, operator: OperatorContext): AlertRule {
        enforceTenant(operator, command.tenantId)
        val now = clock.instant()
        val rule = AlertRule(
            UUID.randomUUID(),
            command.tenantId,
            command.name,
            command.description,
            command.type,
            command.filterCategory,
            command.filterAction,
            command.filterOutcome,
            command.groupByField,
            command.threshold,
            command.window,
            command.severity,
            command.enabled,
            now,
            now,
        )
        val saved = repo.save(rule)
        auditChange(saved, operator, AuditEntry.AuditAction.RULE_CREATED)
        return saved
    }

    @Transactional
    override fun update(
        ruleId: UUID,
        command: DefineAlertRuleUseCase.CreateRuleCommand,
        operator: OperatorContext,
    ): AlertRule {
        val existing = repo.findById(ruleId).orElseThrow { RuleNotFoundException(ruleId) }
        enforceTenant(operator, existing.tenantId)
        val updated = AlertRule(
            existing.ruleId,
            existing.tenantId,
            command.name,
            command.description,
            command.type,
            command.filterCategory,
            command.filterAction,
            command.filterOutcome,
            command.groupByField,
            command.threshold,
            command.window,
            command.severity,
            command.enabled,
            existing.createdAt,
            clock.instant(),
        )
        val saved = repo.save(updated)
        auditChange(saved, operator, AuditEntry.AuditAction.RULE_UPDATED)
        return saved
    }

    @Transactional
    override fun delete(ruleId: UUID, operator: OperatorContext) {
        val existing = repo.findById(ruleId).orElseThrow { RuleNotFoundException(ruleId) }
        enforceTenant(operator, existing.tenantId)
        repo.deleteById(ruleId)
        auditChange(existing, operator, AuditEntry.AuditAction.RULE_DELETED)
    }

    private fun enforceTenant(operator: OperatorContext, tenant: TenantId) {
        if (!operator.canQueryOtherTenant() && operator.tenantId != tenant) {
            throw TenantMismatchException(operator.tenantId, tenant)
        }
        CrossTenantAccessAudit.recordIfCrossTenant(
            audit, clock, operator, tenant, "alert_rule", tenant.value,
        )
    }

    private fun auditChange(rule: AlertRule, operator: OperatorContext, action: AuditEntry.AuditAction) {
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                rule.tenantId,
                clock.instant(),
                operator.subject,
                operator.roles.joinToString(",") { it.name },
                action,
                "alert_rule",
                rule.ruleId.toString(),
                operator.sourceIp,
                mapOf(
                    "name" to rule.name,
                    "type" to rule.type.name,
                    "threshold" to rule.threshold.toString(),
                    "window" to rule.window.toString(),
                    "enabled" to rule.enabled.toString(),
                ),
            )
        )
    }
}
