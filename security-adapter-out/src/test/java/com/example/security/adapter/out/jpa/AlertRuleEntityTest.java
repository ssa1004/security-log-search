package com.example.security.adapter.out.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.adapter.out.jpa.entity.AlertRuleEntity;
import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertRuleEntityTest {

  @Test
  void round_trip() {
    var rule =
        new AlertRule(
            UUID.randomUUID(),
            TenantId.of("acme"),
            "5분 안 5회 인증 실패",
            "brute-force 의심",
            RuleType.THRESHOLD,
            "authentication",
            "logon",
            "failure",
            "source.ip",
            5,
            Duration.ofMinutes(5),
            Severity.HIGH,
            true,
            Instant.parse("2026-05-09T12:00:00Z"),
            Instant.parse("2026-05-09T12:00:00Z"));
    var rt = AlertRuleEntity.from(rule).toDomain();
    assertThat(rt).isEqualTo(rule);
  }
}
