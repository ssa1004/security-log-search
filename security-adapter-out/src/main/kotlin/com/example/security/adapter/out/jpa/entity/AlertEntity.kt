package com.example.security.adapter.out.jpa.entity

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.Alert
import com.example.security.domain.rule.Alert.AlertStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** alerts 테이블 — 발화된 알람. */
@Entity
@Table(
    name = "alerts",
    indexes = [
        Index(name = "ix_alerts_tenant_fired", columnList = "tenant_id,fired_at"),
        Index(name = "ix_alerts_status", columnList = "status"),
    ],
)
class AlertEntity {

    @Id
    @Column(name = "alert_id", nullable = false)
    private var alertId: UUID = UUID(0, 0)

    @Column(name = "tenant_id", nullable = false, length = 32)
    private var tenantId: String = ""

    @Column(name = "rule_id", nullable = false)
    private var ruleId: UUID = UUID(0, 0)

    @Column(name = "rule_name", nullable = false)
    private var ruleName: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private var severity: Severity = Severity.LOW

    @Column(name = "group_key", nullable = false, length = 256)
    private var groupKey: String = ""

    @Column(name = "group_by_field", nullable = false, length = 64)
    private var groupByField: String = ""

    @Column(name = "matched_count", nullable = false)
    private var matchedCount: Int = 0

    @Column(name = "window_start", nullable = false)
    private var windowStart: Instant = Instant.EPOCH

    @Column(name = "window_end", nullable = false)
    private var windowEnd: Instant = Instant.EPOCH

    @Column(name = "fired_at", nullable = false)
    private var firedAt: Instant = Instant.EPOCH

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private var status: AlertStatus = AlertStatus.OPEN

    /** UUID 들을 콤마 join 으로 저장 — 별도 join 테이블 안 만든다 (조회량 적음). */
    @Column(name = "triggering_event_ids", length = 4000)
    private var triggeringEventIds: String? = null

    @Column(length = 1000)
    private var message: String? = null

    fun toDomain(): Alert {
        val ids: List<UUID> = if (triggeringEventIds.isNullOrBlank()) {
            emptyList()
        } else {
            triggeringEventIds!!
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { UUID.fromString(it) }
        }
        return Alert(
            alertId,
            TenantId.of(tenantId),
            ruleId,
            ruleName,
            severity,
            groupKey,
            groupByField,
            matchedCount,
            windowStart,
            windowEnd,
            firedAt,
            status,
            ids,
            message,
        )
    }

    companion object {
        @JvmStatic
        fun from(alert: Alert): AlertEntity = AlertEntity().apply {
            alertId = alert.alertId
            tenantId = alert.tenantId.value
            ruleId = alert.ruleId
            ruleName = alert.ruleName
            severity = alert.severity
            groupKey = alert.groupKey ?: ""
            groupByField = alert.groupByField
            matchedCount = alert.matchedCount
            windowStart = alert.windowStart
            windowEnd = alert.windowEnd
            firedAt = alert.firedAt
            status = alert.status
            triggeringEventIds = alert.triggeringEventIds.joinToString(",") { it.toString() }
            message = alert.message
        }
    }
}
