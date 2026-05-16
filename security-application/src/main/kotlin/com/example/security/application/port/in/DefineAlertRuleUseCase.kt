package com.example.security.application.port.`in`

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import java.time.Duration
import java.util.UUID

/**
 * use case 4 — 운영자가 알람 룰 CRUD.
 *
 * Postgres 영속 + Flink 의 broadcast state 로 hot reload (룰 추가 / 변경 시 Flink job 재시작
 * 불필요).
 */
interface DefineAlertRuleUseCase {

    fun create(command: CreateRuleCommand, operator: OperatorContext): AlertRule

    fun update(ruleId: UUID, command: CreateRuleCommand, operator: OperatorContext): AlertRule

    fun delete(ruleId: UUID, operator: OperatorContext)

    @JvmRecord
    data class CreateRuleCommand(
        val tenantId: TenantId,
        val name: String,
        val description: String?,
        val type: AlertRule.RuleType,
        val filterCategory: String?,
        val filterAction: String?,
        val filterOutcome: String?,
        val groupByField: String,
        val threshold: Int,
        val window: Duration,
        val severity: Severity,
        val enabled: Boolean,
    )
}
