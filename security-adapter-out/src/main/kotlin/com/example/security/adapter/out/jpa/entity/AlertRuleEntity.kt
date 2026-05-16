package com.example.security.adapter.out.jpa.entity

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import com.example.security.domain.rule.AlertRule.RuleType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** alert_rules 테이블. */
@Entity
@Table(
    name = "alert_rules",
    indexes = [
        Index(name = "ix_alert_rules_tenant_enabled", columnList = "tenant_id,enabled"),
    ],
)
class AlertRuleEntity {

    @Id
    @Column(name = "rule_id", nullable = false)
    @get:JvmName("getRuleId")
    var ruleId: UUID = UUID(0, 0)
        private set

    @Column(name = "tenant_id", nullable = false, length = 32)
    private var tenantId: String = ""

    @Column(nullable = false, length = 200)
    private var name: String = ""

    @Column(length = 1000)
    private var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private var type: RuleType = RuleType.THRESHOLD

    @Column(name = "filter_category", length = 64)
    private var filterCategory: String? = null

    @Column(name = "filter_action", length = 64)
    private var filterAction: String? = null

    @Column(name = "filter_outcome", length = 16)
    private var filterOutcome: String? = null

    @Column(name = "group_by_field", nullable = false, length = 64)
    private var groupByField: String = ""

    @Column(name = "threshold", nullable = false)
    private var threshold: Int = 0

    @Column(name = "window_seconds", nullable = false)
    private var windowSeconds: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private var severity: Severity = Severity.LOW

    @Column(nullable = false)
    private var enabled: Boolean = false

    @Column(name = "created_at", nullable = false)
    private var createdAt: Instant = Instant.EPOCH

    @Column(name = "updated_at", nullable = false)
    private var updatedAt: Instant = Instant.EPOCH

    fun toDomain(): AlertRule =
        AlertRule(
            ruleId,
            TenantId.of(tenantId),
            name,
            description,
            type,
            filterCategory,
            filterAction,
            filterOutcome,
            groupByField,
            threshold,
            Duration.ofSeconds(windowSeconds),
            severity,
            enabled,
            createdAt,
            updatedAt,
        )

    companion object {
        @JvmStatic
        fun from(rule: AlertRule): AlertRuleEntity = AlertRuleEntity().apply {
            ruleId = rule.ruleId
            tenantId = rule.tenantId.value
            name = rule.name
            description = rule.description
            type = rule.type
            filterCategory = rule.filterCategory
            filterAction = rule.filterAction
            filterOutcome = rule.filterOutcome
            groupByField = rule.groupByField
            threshold = rule.threshold
            windowSeconds = rule.window.toSeconds()
            severity = rule.severity
            enabled = rule.enabled
            createdAt = rule.createdAt
            updatedAt = rule.updatedAt
        }
    }
}
