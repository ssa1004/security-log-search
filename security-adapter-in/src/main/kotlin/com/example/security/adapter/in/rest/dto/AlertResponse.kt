package com.example.security.adapter.`in`.rest.dto

import com.example.security.domain.rule.Alert
import java.time.Instant
import java.util.UUID

/**
 * 알람 응답 — Flink 가 emit 한 Alert 를 wire format 으로 평탄화.
 *
 * tenantId / severity / status 는 도메인 enum / value object 를 String 으로 외부 노출한다.
 */
@JvmRecord
data class AlertResponse(
    val alertId: UUID,
    val tenantId: String,
    val ruleId: UUID,
    val ruleName: String,
    val severity: String,
    val groupKey: String?,
    val groupByField: String,
    val matchedCount: Int,
    val windowStart: Instant,
    val windowEnd: Instant,
    val firedAt: Instant,
    val status: String,
    val triggeringEventIds: List<UUID>,
    val message: String?,
) {

    companion object {
        @JvmStatic
        fun from(a: Alert): AlertResponse =
            AlertResponse(
                a.alertId,
                a.tenantId.value,
                a.ruleId,
                a.ruleName,
                a.severity.name,
                a.groupKey,
                a.groupByField,
                a.matchedCount,
                a.windowStart,
                a.windowEnd,
                a.firedAt,
                a.status.name,
                a.triggeringEventIds,
                a.message,
            )
    }
}
