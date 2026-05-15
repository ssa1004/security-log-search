package com.example.security.domain.rule

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import java.io.Serializable
import java.time.Instant
import java.util.Objects
import java.util.UUID

/**
 * 발화된 알람 — 룰이 매칭되어 Flink job 이 emit 한 결과.
 *
 * `triggeringEventIds` 는 본 알람을 트리거한 원본 LogEvent 들의 id 목록 (수사관이 drill-down
 * 할 때 사용). 보존 정책상 ID 만 보관, 본문은 OpenSearch 에서 lookup.
 *
 * `triggeringEventIds` 는 생성 시 불변 복사본으로 보관한다 — Java record 호환을 위해 일반
 * class + custom equals/hashCode, accessor 이름은 `alertId()` 형태를 유지한다. Flink 스트림
 * 타입이므로 Serializable 도 그대로 둔다.
 */
class Alert(
    alertId: UUID,
    tenantId: TenantId,
    ruleId: UUID,
    ruleName: String,
    severity: Severity,
    /** 룰의 그룹 키 값 — 예: 192.168.1.10 (source.ip 룰의 경우). */
    groupKey: String?,
    groupByField: String,
    matchedCount: Int,
    windowStart: Instant,
    windowEnd: Instant,
    firedAt: Instant,
    status: AlertStatus,
    triggeringEventIds: List<UUID>,
    message: String?,
) : Serializable {

    @get:JvmName("alertId")
    val alertId: UUID = alertId

    @get:JvmName("tenantId")
    val tenantId: TenantId = tenantId

    @get:JvmName("ruleId")
    val ruleId: UUID = ruleId

    @get:JvmName("ruleName")
    val ruleName: String = ruleName

    @get:JvmName("severity")
    val severity: Severity = severity

    @get:JvmName("groupKey")
    val groupKey: String? = groupKey

    @get:JvmName("groupByField")
    val groupByField: String = groupByField

    @get:JvmName("matchedCount")
    val matchedCount: Int = matchedCount

    @get:JvmName("windowStart")
    val windowStart: Instant = windowStart

    @get:JvmName("windowEnd")
    val windowEnd: Instant = windowEnd

    @get:JvmName("firedAt")
    val firedAt: Instant = firedAt

    @get:JvmName("status")
    val status: AlertStatus = status

    @get:JvmName("triggeringEventIds")
    val triggeringEventIds: List<UUID> = java.util.List.copyOf(triggeringEventIds)

    @get:JvmName("message")
    val message: String? = message

    fun acknowledge(): Alert =
        Alert(
            alertId,
            tenantId,
            ruleId,
            ruleName,
            severity,
            groupKey,
            groupByField,
            matchedCount,
            windowStart,
            windowEnd,
            firedAt,
            AlertStatus.ACKNOWLEDGED,
            triggeringEventIds,
            message,
        )

    fun resolve(): Alert =
        Alert(
            alertId,
            tenantId,
            ruleId,
            ruleName,
            severity,
            groupKey,
            groupByField,
            matchedCount,
            windowStart,
            windowEnd,
            firedAt,
            AlertStatus.RESOLVED,
            triggeringEventIds,
            message,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Alert) return false
        return alertId == other.alertId &&
            tenantId == other.tenantId &&
            ruleId == other.ruleId &&
            ruleName == other.ruleName &&
            severity == other.severity &&
            groupKey == other.groupKey &&
            groupByField == other.groupByField &&
            matchedCount == other.matchedCount &&
            windowStart == other.windowStart &&
            windowEnd == other.windowEnd &&
            firedAt == other.firedAt &&
            status == other.status &&
            triggeringEventIds == other.triggeringEventIds &&
            message == other.message
    }

    override fun hashCode(): Int =
        Objects.hash(
            alertId,
            tenantId,
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
            triggeringEventIds,
            message,
        )

    override fun toString(): String =
        "Alert[alertId=$alertId, tenantId=$tenantId, ruleId=$ruleId, ruleName=$ruleName, " +
            "severity=$severity, groupKey=$groupKey, groupByField=$groupByField, " +
            "matchedCount=$matchedCount, windowStart=$windowStart, windowEnd=$windowEnd, " +
            "firedAt=$firedAt, status=$status, triggeringEventIds=$triggeringEventIds, " +
            "message=$message]"

    enum class AlertStatus {
        OPEN,
        ACKNOWLEDGED,
        RESOLVED,
        FALSE_POSITIVE,
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
