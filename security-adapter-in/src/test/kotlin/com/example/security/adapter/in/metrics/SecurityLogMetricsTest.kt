package com.example.security.adapter.`in`.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SecurityLogMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: SecurityLogMetrics

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = SecurityLogMetrics(registry)
    }

    @Test
    fun `ingest counter 가 source 와 tenant 별로 분리`() {
        metrics.recordIngest("aws", "acme", "aws-cloudtrail")
        metrics.recordIngest("aws", "acme", "aws-cloudtrail")
        metrics.recordIngest("k8s", "acme", "k8s-audit")
        metrics.recordIngest("aws", "globex", "aws-cloudtrail")

        assertThat(
            registry.find(SecurityLogMetrics.INGEST_TOTAL)
                .tag("source", "aws")
                .tag("tenant", "acme")
                .tag("schema", "aws-cloudtrail")
                .counter()!!
                .count(),
        ).isEqualTo(2.0)
        assertThat(
            registry.find(SecurityLogMetrics.INGEST_TOTAL)
                .tag("source", "k8s")
                .tag("tenant", "acme")
                .counter()!!
                .count(),
        ).isEqualTo(1.0)
        assertThat(
            registry.find(SecurityLogMetrics.INGEST_TOTAL)
                .tag("source", "aws")
                .tag("tenant", "globex")
                .counter()!!
                .count(),
        ).isEqualTo(1.0)
    }

    @Test
    fun `normalize failure counter`() {
        metrics.recordNormalizeFailure("aws", "aws-cloudtrail", "unsupported_schema")
        metrics.recordNormalizeFailure("aws", "aws-cloudtrail", "validation_failed")
        metrics.recordNormalizeFailure("aws", "aws-cloudtrail", "validation_failed")

        assertThat(
            registry.find(SecurityLogMetrics.NORMALIZE_FAILURES_TOTAL)
                .tag("reason", "validation_failed")
                .counter()!!
                .count(),
        ).isEqualTo(2.0)
    }

    @Test
    fun `search latency timer 가 histogram 형태`() {
        metrics.recordSearchLatency("acme", "opensearch", Duration.ofMillis(50))
        metrics.recordSearchLatency("acme", "opensearch", Duration.ofMillis(120))
        metrics.recordSearchLatency("acme", "clickhouse", Duration.ofMillis(800))

        val openSearch = registry.find(SecurityLogMetrics.SEARCH_LATENCY)
            .tag("tenant", "acme")
            .tag("type", "opensearch")
            .timer()
        assertThat(openSearch).isNotNull
        assertThat(openSearch!!.count()).isEqualTo(2L)
        assertThat(openSearch.totalTime(TimeUnit.MILLISECONDS)).isBetween(170.0, 175.0)

        val clickHouse = registry.find(SecurityLogMetrics.SEARCH_LATENCY)
            .tag("type", "clickhouse")
            .timer()
        assertThat(clickHouse).isNotNull
        assertThat(clickHouse!!.count()).isEqualTo(1L)
    }

    @Test
    fun `alert fired counter 가 rule severity tenant 별로 분리`() {
        metrics.recordAlertFired("rule-1", "HIGH", "acme")
        metrics.recordAlertFired("rule-1", "HIGH", "acme")
        metrics.recordAlertFired("rule-2", "CRITICAL", "acme")

        assertThat(
            registry.find(SecurityLogMetrics.ALERT_FIRED_TOTAL)
                .tag("rule_id", "rule-1")
                .counter()!!
                .count(),
        ).isEqualTo(2.0)
        assertThat(
            registry.find(SecurityLogMetrics.ALERT_FIRED_TOTAL)
                .tag("rule_id", "rule-2")
                .counter()!!
                .count(),
        ).isEqualTo(1.0)
    }

    @Test
    fun `null 또는 빈 tag 는 unknown 으로 치환`() {
        metrics.recordIngest(null, "", "ecs")
        val counter = registry.find(SecurityLogMetrics.INGEST_TOTAL)
            .tag("source", "unknown")
            .tag("tenant", "unknown")
            .tag("schema", "ecs")
            .counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `너무 긴 tag 값은 64자로 truncate`() {
        val longTenant = "a".repeat(200)
        metrics.recordIngest("aws", longTenant, "ecs")
        val counter = registry.find(SecurityLogMetrics.INGEST_TOTAL)
            .tag("tenant", "a".repeat(64))
            .counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }
}
