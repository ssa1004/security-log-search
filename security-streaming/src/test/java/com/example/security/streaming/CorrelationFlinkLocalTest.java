package com.example.security.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.LogEvent;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import com.example.security.streaming.operator.CorrelationProcessFunction;
import com.example.security.streaming.operator.RuleEvaluator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Flink LocalExecutionEnvironment 가 Java 17+ 에서 record 직렬화를 못 하는 알려진 문제 (Flink
 * 1.19 이상에서 해결) 가 있어, 본 테스트는 Flink runtime 의존성 없이 ProcessFunction 의 핵심
 * 로직 (RuleEvaluator + EvaluatorStateMap) 만 직접 호출하는 형태로 검증한다.
 *
 * <p>운영 클러스터 검증 (실제 jobmanager 위) 은 docker compose / k8s 매니페스트로 분리.
 */
class CorrelationFlinkLocalTest {

  @Test
  void 룰_평가_엔진_상태_맵_시퀀스_시뮬레이션() {
    var tenant = TenantId.of("acme");
    var ruleId = UUID.randomUUID();
    var rule = bruteForceRule(ruleId, tenant);

    // 5회 실패 → 1건 알람.
    var baseTs = Instant.parse("2026-05-09T12:00:00Z");
    var collected = new ArrayList<String>();

    var stateMap = new CorrelationProcessFunction.EvaluatorStateMap();
    // processElement 가 쓰는 것과 같은 (ruleId|groupKey) 복합 키 — 한 tenant 안에서 룰 / 그룹
    // 키 조합별로 evaluator state 가 분산된다.
    Map<String, RuleEvaluator> evaluators = stateMap.evaluators;

    for (int i = 0; i < 5; i++) {
      var event = failureAt(tenant, baseTs.plusSeconds(i * 30L));
      var groupKey = rule.extractGroupKey(event);
      var stateKey = CorrelationProcessFunction.stateKey(ruleId, groupKey);
      var ev =
          evaluators.computeIfAbsent(
              stateKey, k -> new RuleEvaluator(rule, groupKey, UUID::randomUUID));
      var alerts = ev.onEvent(event);
      var ser = new com.example.security.streaming.serde.AlertJsonSerializer();
      alerts.forEach(a -> collected.add(ser.serializeToString(a)));
    }

    assertThat(collected).hasSizeGreaterThanOrEqualTo(1);
    assertThat(collected.get(0)).contains("\"matchedCount\":5");
    assertThat(collected.get(0)).contains("\"groupKey\":\"192.168.1.10\"");
    assertThat(collected.get(0)).contains("\"tenantId\":\"acme\"");
  }

  @Test
  void 두_그룹키가_독립적으로_평가된다() {
    var tenant = TenantId.of("acme");
    var ruleId = UUID.randomUUID();
    var rule = bruteForceRule(ruleId, tenant);

    var baseTs = Instant.parse("2026-05-09T12:00:00Z");
    Map<String, RuleEvaluator> byGroupKey = new HashMap<>();
    var collected = new ArrayList<String>();
    var ser = new com.example.security.streaming.serde.AlertJsonSerializer();

    // 그룹 A — 5회 (알람), 그룹 B — 4회 (알람 X).
    var events = new ArrayList<LogEvent>();
    for (int i = 0; i < 5; i++) {
      events.add(failureWith(tenant, baseTs.plusSeconds(i * 30L), "192.168.1.10"));
    }
    for (int i = 0; i < 4; i++) {
      events.add(failureWith(tenant, baseTs.plusSeconds(i * 30L), "10.0.0.5"));
    }

    for (var e : events) {
      var gk = rule.extractGroupKey(e);
      var ev = byGroupKey.computeIfAbsent(gk, k -> new RuleEvaluator(rule, k, UUID::randomUUID));
      ev.onEvent(e).forEach(a -> collected.add(ser.serializeToString(a)));
    }

    assertThat(collected).hasSize(1);
    assertThat(collected.get(0)).contains("\"groupKey\":\"192.168.1.10\"");
  }

  @Test
  void 한_tenant_의_여러_룰이_복합_키로_분리되어_평가된다() {
    // 스트림은 tenantId 로만 keyBy 되므로, 같은 tenant 의 두 룰이 한 operator 의 state 맵을
    // 공유한다. (ruleId|groupKey) 복합 키 덕에 룰별 / 그룹 키별 state 가 섞이지 않아야 한다.
    var tenant = TenantId.of("acme");
    var ruleA = bruteForceRule(UUID.randomUUID(), tenant); // source.ip 기준 5회
    var ruleB =
        new AlertRule(
            UUID.randomUUID(),
            tenant,
            "user 기준 5회 인증 실패",
            "brute-force per user",
            RuleType.THRESHOLD,
            "authentication",
            "logon",
            "failure",
            "user.name",
            5,
            Duration.ofMinutes(5),
            Severity.HIGH,
            true,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"));

    var stateMap = new CorrelationProcessFunction.EvaluatorStateMap();
    var evaluators = stateMap.evaluators;
    var ser = new com.example.security.streaming.serde.AlertJsonSerializer();
    var collected = new ArrayList<String>();

    var baseTs = Instant.parse("2026-05-09T12:00:00Z");
    for (int i = 0; i < 5; i++) {
      var event = failureWith(tenant, baseTs.plusSeconds(i * 30L), "192.168.1.10");
      // processElement 가 룰 broadcast 전체를 순회하는 것과 동일하게 두 룰 모두 적용.
      for (var rule : java.util.List.of(ruleA, ruleB)) {
        var gk = rule.extractGroupKey(event);
        var key = CorrelationProcessFunction.stateKey(rule.ruleId(), gk);
        var ev =
            evaluators.computeIfAbsent(key, k -> new RuleEvaluator(rule, gk, UUID::randomUUID));
        ev.onEvent(event).forEach(a -> collected.add(ser.serializeToString(a)));
      }
    }

    // 두 룰 모두 5회 임계 도달 — ruleA 는 192.168.1.10, ruleB 는 alice 그룹 키로 각각 1건.
    assertThat(evaluators).hasSize(2);
    assertThat(collected).hasSize(2);
    assertThat(collected).anyMatch(s -> s.contains("\"groupKey\":\"192.168.1.10\""));
    assertThat(collected).anyMatch(s -> s.contains("\"groupKey\":\"alice\""));
  }

  private AlertRule bruteForceRule(UUID ruleId, TenantId tenant) {
    var t = Instant.parse("2026-01-01T00:00:00Z");
    return new AlertRule(
        ruleId,
        tenant,
        "5분 안 5회 인증 실패",
        "brute-force",
        RuleType.THRESHOLD,
        "authentication",
        "logon",
        "failure",
        "source.ip",
        5,
        Duration.ofMinutes(5),
        Severity.HIGH,
        true,
        t,
        t);
  }

  private LogEvent failureAt(TenantId tenant, Instant ts) {
    return failureWith(tenant, ts, "192.168.1.10");
  }

  private LogEvent failureWith(TenantId tenant, Instant ts, String ip) {
    return new LogEvent(
        UUID.randomUUID(),
        tenant,
        ts,
        ts.plusSeconds(1),
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
        "alice",
        "host-1",
        "linux",
        "Failed login",
        java.util.Map.of());
  }
}
