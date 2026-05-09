package com.example.security.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.LogEvent;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import com.example.security.streaming.operator.RuleEvaluator;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleEvaluatorTest {

  private final TenantId tenantId = TenantId.of("acme");
  private final UUID ruleId = UUID.randomUUID();
  private final java.time.Instant baseTs = java.time.Instant.parse("2026-05-09T12:00:00Z");

  @Test
  void THRESHOLD_5회_도달시_즉시_알람() {
    var rule = thresholdRule();
    var evaluator = new RuleEvaluator(rule, "192.168.1.10", UUID::randomUUID);

    for (int i = 0; i < 4; i++) {
      var alerts = evaluator.onEvent(failureAt(baseTs.plusSeconds(i * 30L)));
      assertThat(alerts).isEmpty();
    }
    var alerts = evaluator.onEvent(failureAt(baseTs.plusSeconds(120)));
    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).matchedCount()).isEqualTo(5);
    assertThat(alerts.get(0).groupKey()).isEqualTo("192.168.1.10");
  }

  @Test
  void 윈도우_밖_옛_이벤트는_evict() {
    var rule = thresholdRule();
    var evaluator = new RuleEvaluator(rule, "192.168.1.10", UUID::randomUUID);

    // 4건 발생 후 6분 뒤 1건 더 — 옛 4건은 evict 되어야 함.
    for (int i = 0; i < 4; i++) {
      evaluator.onEvent(failureAt(baseTs.plusSeconds(i)));
    }
    var alerts = evaluator.onEvent(failureAt(baseTs.plus(Duration.ofMinutes(6))));
    assertThat(alerts).isEmpty();
    assertThat(evaluator.windowSize()).isEqualTo(1);
  }

  @Test
  void SEQUENCE_5회_실패_후_1회_성공시_알람() {
    var rule = sequenceRule();
    var evaluator = new RuleEvaluator(rule, "192.168.1.10", UUID::randomUUID);

    for (int i = 0; i < 5; i++) {
      evaluator.onEvent(failureAt(baseTs.plusSeconds(i * 10L)));
    }
    // 5회 실패만으로는 SEQUENCE 룰은 알람 X.
    var alerts = evaluator.onEvent(successAt(baseTs.plusSeconds(60)));
    assertThat(alerts).hasSize(1);
    assertThat(alerts.get(0).message()).contains("성공");
    assertThat(alerts.get(0).matchedCount()).isEqualTo(6); // 실패 5 + 성공 1
  }

  @Test
  void SEQUENCE_5회_실패만으로는_알람_없음() {
    var rule = sequenceRule();
    var evaluator = new RuleEvaluator(rule, "192.168.1.10", UUID::randomUUID);

    for (int i = 0; i < 7; i++) {
      var alerts = evaluator.onEvent(failureAt(baseTs.plusSeconds(i * 10L)));
      assertThat(alerts).isEmpty();
    }
  }

  @Test
  void SEQUENCE_threshold_도달_후_윈도우_지나서_성공이면_알람_없음() {
    var rule = sequenceRule();
    var evaluator = new RuleEvaluator(rule, "192.168.1.10", UUID::randomUUID);

    for (int i = 0; i < 5; i++) {
      evaluator.onEvent(failureAt(baseTs.plusSeconds(i * 10L)));
    }
    // 윈도우 5분 — 6분 뒤 성공.
    var alerts = evaluator.onEvent(successAt(baseTs.plus(Duration.ofMinutes(7))));
    assertThat(alerts).isEmpty();
  }

  @Test
  void onTimer_가_옛_이벤트_정리() {
    var rule = thresholdRule();
    var evaluator = new RuleEvaluator(rule, "192.168.1.10", UUID::randomUUID);

    for (int i = 0; i < 3; i++) {
      evaluator.onEvent(failureAt(baseTs.plusSeconds(i)));
    }
    evaluator.onTimer(baseTs.plus(Duration.ofMinutes(10)));
    assertThat(evaluator.windowSize()).isZero();
  }

  private AlertRule thresholdRule() {
    return new AlertRule(
        ruleId,
        tenantId,
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
        baseTs,
        baseTs);
  }

  private AlertRule sequenceRule() {
    return new AlertRule(
        ruleId,
        tenantId,
        "5회 실패 직후 성공",
        "brute-force 침입 시퀀스",
        RuleType.SEQUENCE,
        "authentication",
        "logon",
        "failure",
        "source.ip",
        5,
        Duration.ofMinutes(5),
        Severity.CRITICAL,
        true,
        baseTs,
        baseTs);
  }

  private LogEvent failureAt(Instant ts) {
    return new LogEvent(
        UUID.randomUUID(),
        tenantId,
        ts,
        ts.plusSeconds(1),
        "event",
        "authentication",
        "denied",
        "logon",
        "failure",
        Severity.MEDIUM,
        "192.168.1.10",
        12345,
        null,
        null,
        "alice",
        "host-1",
        "linux",
        "Failed login",
        Map.of());
  }

  private LogEvent successAt(Instant ts) {
    return new LogEvent(
        UUID.randomUUID(),
        tenantId,
        ts,
        ts.plusSeconds(1),
        "event",
        "authentication",
        "allowed",
        "logon",
        "success",
        Severity.INFO,
        "192.168.1.10",
        12345,
        null,
        null,
        "alice",
        "host-1",
        "linux",
        "Login success",
        Map.of());
  }
}
