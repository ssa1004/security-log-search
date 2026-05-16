package com.example.security.application.port.out

import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import java.util.Optional
import java.util.UUID

/** 알람 룰 영속 — Postgres alert_rules 테이블. */
interface AlertRuleRepository {

    fun save(rule: AlertRule): AlertRule

    fun findById(ruleId: UUID): Optional<AlertRule>

    fun findEnabledByTenant(tenantId: TenantId): List<AlertRule>

    fun findAllEnabled(): List<AlertRule>

    fun deleteById(ruleId: UUID)
}
