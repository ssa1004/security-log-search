package com.example.security.streaming.operator

import com.example.security.domain.event.LogEvent
import com.example.security.domain.rule.Alert
import com.example.security.domain.rule.Alert.AlertStatus
import com.example.security.domain.rule.AlertRule
import com.example.security.domain.rule.AlertRule.RuleType
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.Deque
import java.util.UUID
import java.util.function.Supplier

/**
 * Flink `KeyedProcessFunction` 의 알맹이를 단위 테스트 가능한 형태로 분리한 평가 엔진.
 *
 * 본 클래스는 deterministic — 같은 룰 + 같은 이벤트 시퀀스를 넣으면 같은 알람이 나온다.
 * Flink runtime 이 없는 단위 테스트에서 핵심 로직만 검증할 수 있다.
 *
 * 알고리즘:
 * - 같은 룰 + 같은 그룹 키 안에서 슬라이딩 윈도우 (이벤트 도착 시각 기준)
 * - 윈도우 안 카운트가 룰의 threshold 도달하면:
 *     - THRESHOLD: 즉시 알람
 *     - SEQUENCE: 다음 직후 1회 성공 이벤트가 같은 그룹 키에서 들어오면 알람
 * - 윈도우보다 옛 이벤트는 evict
 *
 * Flink keyed state 에 ValueState 로 저장되므로 [Serializable] 을 유지한다.
 */
class RuleEvaluator(
    private val rule: AlertRule,
    private val groupKey: String,
    private val alertIdSupplier: Supplier<UUID>,
) : Serializable {

    /** 그룹 키 (예: 192.168.1.10) 단위로 카운트. */
    private val window: Deque<EventEntry> = ArrayDeque()

    /** SEQUENCE 룰: 가장 최근 threshold 도달 시각 (성공 이벤트 도착 시 알람 생성). */
    private var thresholdReachedAt: Instant? = null

    /**
     * 새 이벤트 처리. 알람이 발화되면 결과를 반환, 아니면 빈 list.
     *
     * 이벤트는 룰의 filter 에 매칭되었거나 (THRESHOLD), 또는 SEQUENCE 의 trailing success 도
     * 본 메서드로 들어와야 한다 — 호출 측이 책임.
     */
    fun onEvent(event: LogEvent): List<Alert> {
        val now = event.timestamp
        evictOlderThan(now.minus(rule.window))

        val alerts = ArrayList<Alert>()

        if (rule.matches(event)) {
            window.add(EventEntry(event.eventId, now))
            if (window.size >= rule.threshold) {
                when (rule.type) {
                    RuleType.THRESHOLD -> alerts.add(buildAlert(now, snapshotIds(), "임계값 초과"))
                    RuleType.SEQUENCE ->
                        // trailing success 가 도착할 때까지 시각만 보관.
                        thresholdReachedAt = now
                }
            }
        } else if (rule.type == RuleType.SEQUENCE &&
            thresholdReachedAt != null &&
            isTrailingSuccess(event)
        ) {
            // threshold 도달 후 윈도우 안 trailing success → 알람.
            val dt = Duration.between(thresholdReachedAt, now)
            if (!dt.isNegative && dt <= rule.window) {
                val ids = snapshotIds()
                ids.add(event.eventId)
                alerts.add(buildAlert(now, ids, "실패 시퀀스 직후 성공 (brute-force 의심)"))
                thresholdReachedAt = null
                window.clear()
            }
        }

        return alerts
    }

    /** Flink Timer 가 윈도우 만료 시 호출 — state 정리만. */
    fun onTimer(now: Instant) {
        evictOlderThan(now.minus(rule.window))
        if (window.isEmpty()) {
            thresholdReachedAt = null
        }
    }

    /** 같은 룰의 그룹 키 안에서 trailing success 판정 — 룰의 그룹 키 추출과 동일하면 매칭. */
    private fun isTrailingSuccess(event: LogEvent): Boolean {
        if (event.tenantId != rule.tenantId) return false
        if (rule.filterCategory != null && rule.filterCategory != event.eventCategory) return false
        if (rule.filterAction != null && rule.filterAction != event.eventAction) return false
        if ("success" != event.eventOutcome) return false
        return groupKey == rule.extractGroupKey(event)
    }

    private fun evictOlderThan(cutoff: Instant) {
        while (window.isNotEmpty() && window.peekFirst().timestamp.isBefore(cutoff)) {
            window.pollFirst()
        }
    }

    private fun snapshotIds(): ArrayList<UUID> {
        val out = ArrayList<UUID>(window.size)
        for (e in window) out.add(e.eventId)
        return out
    }

    private fun buildAlert(firedAt: Instant, ids: List<UUID>, message: String): Alert =
        Alert(
            alertIdSupplier.get(),
            rule.tenantId,
            rule.ruleId,
            rule.name,
            rule.severity,
            groupKey,
            rule.groupByField,
            ids.size,
            firedAt.minus(rule.window),
            firedAt,
            firedAt,
            AlertStatus.OPEN,
            ids,
            message,
        )

    /** 단위 테스트용 — 현재 윈도우 사이즈. */
    fun windowSize(): Int = window.size

    /** 내부 entry — Flink state 직렬화 가능. */
    private data class EventEntry(val eventId: UUID, val timestamp: Instant) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
