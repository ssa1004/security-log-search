package com.example.security.adapter.in.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityLogMetricsTest {

  private SimpleMeterRegistry registry;
  private SecurityLogMetrics metrics;

  @BeforeEach
  void setup() {
    registry = new SimpleMeterRegistry();
    metrics = new SecurityLogMetrics(registry);
  }

  @Test
  void ingest_counter_가_source_와_tenant_별로_분리() {
    metrics.recordIngest("aws", "acme", "aws-cloudtrail");
    metrics.recordIngest("aws", "acme", "aws-cloudtrail");
    metrics.recordIngest("k8s", "acme", "k8s-audit");
    metrics.recordIngest("aws", "globex", "aws-cloudtrail");

    assertThat(
            registry
                .find(SecurityLogMetrics.INGEST_TOTAL)
                .tag("source", "aws")
                .tag("tenant", "acme")
                .tag("schema", "aws-cloudtrail")
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry
                .find(SecurityLogMetrics.INGEST_TOTAL)
                .tag("source", "k8s")
                .tag("tenant", "acme")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .find(SecurityLogMetrics.INGEST_TOTAL)
                .tag("source", "aws")
                .tag("tenant", "globex")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void normalize_failure_counter() {
    metrics.recordNormalizeFailure("aws", "aws-cloudtrail", "unsupported_schema");
    metrics.recordNormalizeFailure("aws", "aws-cloudtrail", "validation_failed");
    metrics.recordNormalizeFailure("aws", "aws-cloudtrail", "validation_failed");

    assertThat(
            registry
                .find(SecurityLogMetrics.NORMALIZE_FAILURES_TOTAL)
                .tag("reason", "validation_failed")
                .counter()
                .count())
        .isEqualTo(2.0);
  }

  @Test
  void search_latency_timer_가_histogram_형태() {
    metrics.recordSearchLatency("acme", "opensearch", Duration.ofMillis(50));
    metrics.recordSearchLatency("acme", "opensearch", Duration.ofMillis(120));
    metrics.recordSearchLatency("acme", "clickhouse", Duration.ofMillis(800));

    var openSearch =
        registry
            .find(SecurityLogMetrics.SEARCH_LATENCY)
            .tag("tenant", "acme")
            .tag("type", "opensearch")
            .timer();
    assertThat(openSearch).isNotNull();
    assertThat(openSearch.count()).isEqualTo(2L);
    assertThat(openSearch.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
        .isBetween(170.0, 175.0);

    var clickHouse =
        registry
            .find(SecurityLogMetrics.SEARCH_LATENCY)
            .tag("type", "clickhouse")
            .timer();
    assertThat(clickHouse).isNotNull();
    assertThat(clickHouse.count()).isEqualTo(1L);
  }

  @Test
  void alert_fired_counter_가_rule_severity_tenant_별로_분리() {
    metrics.recordAlertFired("rule-1", "HIGH", "acme");
    metrics.recordAlertFired("rule-1", "HIGH", "acme");
    metrics.recordAlertFired("rule-2", "CRITICAL", "acme");

    assertThat(
            registry
                .find(SecurityLogMetrics.ALERT_FIRED_TOTAL)
                .tag("rule_id", "rule-1")
                .counter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry
                .find(SecurityLogMetrics.ALERT_FIRED_TOTAL)
                .tag("rule_id", "rule-2")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void null_또는_빈_tag_는_unknown_으로_치환() {
    metrics.recordIngest(null, "", "ecs");
    var counter =
        registry
            .find(SecurityLogMetrics.INGEST_TOTAL)
            .tag("source", "unknown")
            .tag("tenant", "unknown")
            .tag("schema", "ecs")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void 너무_긴_tag_값은_64자로_truncate() {
    var longTenant = "a".repeat(200);
    metrics.recordIngest("aws", longTenant, "ecs");
    var counter =
        registry
            .find(SecurityLogMetrics.INGEST_TOTAL)
            .tag("tenant", "a".repeat(64))
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }
}
