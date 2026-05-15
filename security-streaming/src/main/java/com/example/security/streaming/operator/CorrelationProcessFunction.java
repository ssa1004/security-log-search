package com.example.security.streaming.operator;

import com.example.security.domain.event.LogEvent;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.AlertRule;
import com.example.security.streaming.serde.AlertJsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.util.Collector;

/**
 * Flink 의 핵심 operator — broadcast 된 룰을 받아 keyed event stream 에 적용한다.
 *
 * <p>키 디자인:
 *
 * <ul>
 *   <li>이벤트 stream 의 key = tenantId — 룰 / 그룹 키는 keyBy 시점에 알 수 없다 (룰은
 *       broadcast 채널로만 들어옴). 한 tenant 의 모든 룰 / 그룹 키를 같은 operator 에서 처리.
 *   <li>룰 broadcast — 모든 키에서 룰 변경을 즉시 반영 (hot reload, ADR-0008)
 * </ul>
 *
 * <p>운영 시 룰 전체를 broadcast 한다 (룰 수가 < 수천 개 가정). {@code processElement} 가
 * broadcast 된 룰 전체를 순회하며 각 룰의 그룹 키별 {@link RuleEvaluator} state 를 보관 →
 * {@code (ruleId|groupKey) -> RuleEvaluator} 맵을 ValueState 로 직렬화.
 *
 * <p>제약:
 *
 * <ul>
 *   <li>본 구현은 LocalExecutionEnvironment 에서 단위 테스트되도록 설계 (직렬화 가능)
 *   <li>운영 클러스터에서는 RocksDB state backend 권장
 * </ul>
 */
public class CorrelationProcessFunction
    extends KeyedBroadcastProcessFunction<String, LogEvent, AlertRule, String> {

  /** broadcast state 키: ruleId. */
  public static final MapStateDescriptor<UUID, AlertRule> RULES_DESCRIPTOR =
      new MapStateDescriptor<>(
          "alert-rules",
          TypeInformation.of(new TypeHint<UUID>() {}),
          TypeInformation.of(new TypeHint<AlertRule>() {}));

  /** keyed state — (ruleId|groupKey) 단위 evaluator 상태. ValueState 로 한 번에 묶어서 보관. */
  private transient ValueState<EvaluatorStateMap> stateMap;

  private transient AlertJsonSerializer alertSerializer;

  @Override
  public void open(org.apache.flink.configuration.Configuration parameters) {
    var desc = new ValueStateDescriptor<>("eval-state", EvaluatorStateMap.class);
    stateMap = getRuntimeContext().getState(desc);
    alertSerializer = new AlertJsonSerializer();
  }

  @Override
  public void processElement(LogEvent event, ReadOnlyContext ctx, Collector<String> out)
      throws Exception {
    var rules = ctx.getBroadcastState(RULES_DESCRIPTOR);
    var current = stateMap.value();
    if (current == null) current = new EvaluatorStateMap();

    // 스트림은 tenantId 로만 keyBy 되어 있다 — 이 tenant 의 broadcast 룰 전체를 순회하며
    // 룰별 그룹 키 단위로 evaluator state 를 적용한다. 룰 / 그룹 키는 keyBy 시점에 알 수 없어
    // (룰이 broadcast 로만 들어옴) 여기서 fan-out 한다.
    var tenant = event.tenantId();
    boolean changed = false;
    for (Map.Entry<UUID, AlertRule> entry : rules.immutableEntries()) {
      var rule = entry.getValue();
      if (rule == null || !rule.tenantId().equals(tenant)) continue;

      var groupKey = rule.extractGroupKey(event);
      var stateKey = stateKey(rule.ruleId(), groupKey);
      var evaluator =
          current.evaluators.computeIfAbsent(
              stateKey, k -> new RuleEvaluator(rule, groupKey, UUID::randomUUID));
      var alerts = evaluator.onEvent(event);
      for (Alert a : alerts) {
        out.collect(alertSerializer.serializeToString(a));
      }
      changed = true;
    }

    if (changed) {
      stateMap.update(current);
    }
  }

  /** keyed state 맵의 복합 키 — 한 tenant 안에서 (ruleId, groupKey) 조합을 구분. */
  public static String stateKey(UUID ruleId, String groupKey) {
    return ruleId + "|" + groupKey;
  }

  @Override
  public void processBroadcastElement(AlertRule rule, Context ctx, Collector<String> out)
      throws Exception {
    BroadcastState<UUID, AlertRule> rules = ctx.getBroadcastState(RULES_DESCRIPTOR);
    if (!rule.enabled()) {
      rules.remove(rule.ruleId());
    } else {
      rules.put(rule.ruleId(), rule);
    }
  }

  /**
   * keyed state 의 직렬화 가능 wrapper. ValueState 가 한 번에 set 되도록.
   *
   * <p>키는 {@link #stateKey(UUID, String)} 의 {@code ruleId|groupKey} 복합 키.
   */
  public static class EvaluatorStateMap implements Serializable {
    public Map<String, RuleEvaluator> evaluators = new HashMap<>();
  }

  /** 사용되지 않는 import 제거 방지. */
  @SuppressWarnings("unused")
  private static void unused() {
    new ObjectMapper().registerModule(new JavaTimeModule());
  }
}
