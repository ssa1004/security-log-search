package com.example.security.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.LogEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertRuleTest {

  @Test
  void 인증_실패_5회_5분_룰_매칭() {
    var rule = bruteForceRule();
    var event = authFailure("alice", "192.168.1.10");
    assertThat(rule.matches(event)).isTrue();
    assertThat(rule.extractGroupKey(event)).isEqualTo("192.168.1.10");
  }

  @Test
  void 다른_tenant_이벤트는_매칭_안됨() {
    var rule = bruteForceRule();
    var event = authFailure("alice", "192.168.1.10", TenantId.of("other"));
    assertThat(rule.matches(event)).isFalse();
  }

  @Test
  void disabled_룰은_매칭_안됨() {
    var rule = ruleBuilder().disabledOnly().build();
    assertThat(rule.matches(authFailure("alice", "1.2.3.4"))).isFalse();
  }

  @Test
  void filter_outcome_불일치는_매칭_안됨() {
    var rule = bruteForceRule();
    var success = success("alice", "192.168.1.10");
    assertThat(rule.matches(success)).isFalse();
  }

  @Test
  void user_name_그룹_키() {
    var rule = ruleBuilder().groupByField("user.name").build();
    assertThat(rule.extractGroupKey(authFailure("alice", "192.168.1.10")))
        .isEqualTo("alice");
  }

  @Test
  void unknown_그룹_키는_unknown_으로() {
    var rule = ruleBuilder().groupByField("source.ip").build();
    var event = authFailure("alice", null);
    assertThat(rule.extractGroupKey(event)).isEqualTo("<unknown>");
  }

  @Test
  void threshold_가_0_이하면_거부() {
    assertThatThrownBy(() -> ruleBuilder().threshold(0).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void window_가_1일_초과면_거부() {
    assertThatThrownBy(() -> ruleBuilder().window(Duration.ofDays(2)).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  static AlertRule bruteForceRule() {
    return ruleBuilder().build();
  }

  static RuleBuilder ruleBuilder() {
    return new RuleBuilder();
  }

  static LogEvent authFailure(String user, String ip) {
    return authFailure(user, ip, TenantId.of("acme"));
  }

  static LogEvent authFailure(String user, String ip, TenantId tenant) {
    return new LogEvent(
        UUID.randomUUID(),
        tenant,
        Instant.parse("2026-05-09T12:00:00Z"),
        Instant.parse("2026-05-09T12:00:01Z"),
        "event",
        "authentication",
        "denied",
        "logon",
        "failure",
        Severity.MEDIUM,
        ip,
        12345,
        null,
        null,
        user,
        "host-1",
        "linux",
        "Failed login",
        Map.of());
  }

  static LogEvent success(String user, String ip) {
    return new LogEvent(
        UUID.randomUUID(),
        TenantId.of("acme"),
        Instant.parse("2026-05-09T12:00:00Z"),
        Instant.parse("2026-05-09T12:00:01Z"),
        "event",
        "authentication",
        "allowed",
        "logon",
        "success",
        Severity.INFO,
        ip,
        12345,
        null,
        null,
        user,
        "host-1",
        "linux",
        "Login success",
        Map.of());
  }

  static class RuleBuilder {
    UUID ruleId = UUID.randomUUID();
    TenantId tenantId = TenantId.of("acme");
    String name = "5분 안 5회 인증 실패";
    String description = "brute-force 의심";
    AlertRule.RuleType type = AlertRule.RuleType.THRESHOLD;
    String filterCategory = "authentication";
    String filterAction = "logon";
    String filterOutcome = "failure";
    String groupByField = "source.ip";
    int threshold = 5;
    Duration window = Duration.ofMinutes(5);
    Severity severity = Severity.HIGH;
    boolean enabled = true;

    RuleBuilder groupByField(String v) {
      this.groupByField = v;
      return this;
    }

    RuleBuilder threshold(int v) {
      this.threshold = v;
      return this;
    }

    RuleBuilder window(Duration v) {
      this.window = v;
      return this;
    }

    RuleBuilder disabledOnly() {
      this.enabled = false;
      return this;
    }

    AlertRule build() {
      var now = Instant.parse("2026-05-09T00:00:00Z");
      return new AlertRule(
          ruleId,
          tenantId,
          name,
          description,
          type,
          filterCategory,
          filterAction,
          filterOutcome,
          groupByField,
          threshold,
          window,
          severity,
          enabled,
          now,
          now);
    }
  }
}
