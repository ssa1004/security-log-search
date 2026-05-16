package com.example.security.adapter.`in`.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import org.springframework.stereotype.Component

/**
 * 본 서비스의 운영 메트릭 정의 — Prometheus + Micrometer 모델.
 *
 * RED 모델 (Rate / Errors / Duration) 의 핵심 지표를 한 곳에 모아 컨트롤러 / 서비스가
 * 호출한다. 메트릭 이름 규칙은 Prometheus 컨벤션 (snake_case + suffix `_total` / `_seconds`)
 * 을 따른다.
 *
 * 제공 메트릭:
 *
 *  - `security_log_ingest_total{source, tenant, schema}` — counter, ingest 성공
 *  - `security_log_normalize_failures_total{source, schema, reason}` — counter, 정규화 실패
 *  - `security_log_search_latency_seconds{tenant, type}` — histogram, 검색 지연
 *  - `security_log_alert_fired_total{rule_id, severity, tenant}` — counter, 알람 발생
 *  - `security_log_alert_duplicate_total{tenant}` — counter, alerts.fired 중복 메시지
 *
 * 본 컴포넌트는 동적으로 tag 조합을 생성하므로 cardinality 가 무한이 되지 않도록 주의한다.
 * tenant 가 수천 개로 증가하면 별도 aggregation rule (Prometheus recording rule) 로 압축한다.
 */
@Component
class SecurityLogMetrics(
    private val registry: MeterRegistry,
) {

    fun recordIngest(source: String?, tenant: String?, schema: String?) {
        Counter.builder(INGEST_TOTAL)
            .description("ingest 성공 개수")
            .tag("source", safeTag(source))
            .tag("tenant", safeTag(tenant))
            .tag("schema", safeTag(schema))
            .register(registry)
            .increment()
    }

    fun recordNormalizeFailure(source: String?, schema: String?, reason: String?) {
        Counter.builder(NORMALIZE_FAILURES_TOTAL)
            .description("정규화 실패 개수")
            .tag("source", safeTag(source))
            .tag("schema", safeTag(schema))
            .tag("reason", safeTag(reason))
            .register(registry)
            .increment()
    }

    fun recordSearchLatency(tenant: String?, type: String?, elapsed: Duration) {
        Timer.builder(SEARCH_LATENCY)
            .description("검색 지연 (초)")
            .tag("tenant", safeTag(tenant))
            .tag("type", safeTag(type))
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry)
            .record(elapsed)
    }

    fun recordAlertFired(ruleId: String?, severity: String?, tenant: String?) {
        Counter.builder(ALERT_FIRED_TOTAL)
            .description("알람 발생 개수")
            .tag("rule_id", safeTag(ruleId))
            .tag("severity", safeTag(severity))
            .tag("tenant", safeTag(tenant))
            .register(registry)
            .increment()
    }

    /** alerts.fired 중복 alertId 차단 횟수 — at-least-once delivery 의 운영 가시성. */
    fun recordAlertDuplicate(tenant: String?) {
        Counter.builder(ALERT_DUPLICATE_TOTAL)
            .description("alerts.fired 중복 alertId 차단 횟수 (at-least-once 재전송)")
            .tag("tenant", safeTag(tenant))
            .register(registry)
            .increment()
    }

    companion object {
        /** ingest 성공 counter 이름. */
        const val INGEST_TOTAL: String = "security_log_ingest_total"

        /** 정규화 실패 counter 이름. */
        const val NORMALIZE_FAILURES_TOTAL: String = "security_log_normalize_failures_total"

        /** 검색 지연 timer 이름. */
        const val SEARCH_LATENCY: String = "security_log_search_latency_seconds"

        /** 알람 발생 counter 이름. */
        const val ALERT_FIRED_TOTAL: String = "security_log_alert_fired_total"

        /** alerts.fired 중복 차단 counter 이름. */
        const val ALERT_DUPLICATE_TOTAL: String = "security_log_alert_duplicate_total"

        /**
         * tag 값에 들어올 수 있는 null / 빈문자열 / 너무 긴 문자열 정리. Prometheus tag 는 utf-8 모든
         * 문자를 받지만, 운영 dashboard 에서 정리되지 않은 문자열이 cardinality 를 키운다.
         */
        private fun safeTag(value: String?): String {
            if (value.isNullOrBlank()) return "unknown"
            if (value.length > 64) return value.substring(0, 64)
            return value
        }
    }
}
