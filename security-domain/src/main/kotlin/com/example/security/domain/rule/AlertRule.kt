package com.example.security.domain.rule

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.event.LogEvent
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 알람 룰 — 운영자가 정의하는 상관분석 규칙.
 *
 * 본 시스템에서 알람 룰은 두 종류로 분류된다.
 * - [RuleType.THRESHOLD] — "같은 IP 에서 5분 안 5회 인증 실패" 같이 특정 그룹 키 기준
 *   카운트가 임계값을 넘으면 발화.
 * - [RuleType.SEQUENCE] — "5회 실패 직후 1회 성공" 같이 두 단계 시퀀스 매칭. brute-force
 *   침입 패턴이 대표 예.
 *
 * 룰 평가는 Flink job 의 `KeyedProcessFunction` 이 담당하고, 룰 자체는 PostgreSQL 에
 * 영속된다. 운영자가 룰 추가 / 수정 시 Flink 의 broadcast state 로 hot reload 된다 (Flink job
 * 재시작 불필요).
 */
@JvmRecord
data class AlertRule(
    val ruleId: UUID,
    val tenantId: TenantId,
    val name: String,
    val description: String?,
    val type: RuleType,
    /** ECS event.category 필터 (예: "authentication"). null 이면 전체 매칭. */
    val filterCategory: String?,
    /** ECS event.action 필터 (예: "logon"). null 이면 전체. */
    val filterAction: String?,
    /** ECS event.outcome 필터 (예: "failure"). null 이면 전체. */
    val filterOutcome: String?,
    /** 그룹 키 — "source.ip" / "user.name" / "host.hostname" 등. */
    val groupByField: String,
    /** 임계값 — THRESHOLD 룰의 카운트 임계값. */
    val threshold: Int,
    /** 슬라이딩 윈도우 길이 — 5분 / 1시간 등. */
    val window: Duration,
    /** 발화 시 alert.severity. */
    val severity: Severity,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) : Serializable {

    init {
        require(threshold >= 1) { "threshold 는 1 이상: $threshold" }
        require(!(window.isNegative || window.isZero)) { "window 는 양수: $window" }
        // 1일을 초과하는 윈도우는 streaming state 폭증 위험 → 별도 batch 잡으로 가야 함.
        require(window <= Duration.ofDays(1)) { "window 는 1일 이하: $window" }
    }

    /** 이벤트가 본 룰의 필터에 매칭되는지. */
    fun matches(event: LogEvent): Boolean {
        if (!enabled) return false
        if (event.tenantId != tenantId) return false
        if (filterCategory != null && filterCategory != event.eventCategory) return false
        if (filterAction != null && filterAction != event.eventAction) return false
        if (filterOutcome != null && filterOutcome != event.eventOutcome) return false
        return true
    }

    /** 그룹 키 값 추출 — Flink keyBy 의 키. */
    fun extractGroupKey(event: LogEvent): String =
        when (groupByField) {
            "source.ip" -> nullSafe(event.sourceIp)
            "destination.ip" -> nullSafe(event.destinationIp)
            "user.name" -> nullSafe(event.userName)
            "host.hostname" -> nullSafe(event.hostName)
            "tenant" -> tenantId.value
            else -> nullSafe(event.labels[groupByField])
        }

    enum class RuleType {
        /** 같은 그룹 키에서 윈도우 안 카운트 임계값 초과. */
        THRESHOLD,

        /** 그룹 키 안에서 N회 실패 직후 1회 성공 — brute-force 패턴. */
        SEQUENCE,
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        private fun nullSafe(s: String?): String = s ?: "<unknown>"
    }
}
