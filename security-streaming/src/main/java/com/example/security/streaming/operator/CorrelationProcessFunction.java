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
 *   <li>이벤트 stream 의 key = (tenantId + ruleId + groupKey)
 *   <li>룰 broadcast — 모든 키에서 룰 변경을 즉시 반영 (hot reload, ADR-0008)
 * </ul>
 *
 * <p>운영 시 룰 전체를 broadcast 한다 (룰 수가 < 수천 개 가정). 룰별로 key 마다
 * RuleEvaluator state 를 보관 → ValueState&lt;EvaluatorState&gt; 로 직렬화.
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

  /** keyed state — 그룹 키 단위 evaluator 상태. ValueState 로 (ruleId → state) 묶어서 보관. */
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

    var keyParts = ctx.getCurrentKey().split("\\|", 3);
    if (keyParts.length < 3) return;
    var ruleIdStr = keyParts[1];
    var groupKey = keyParts[2];
    var ruleId = UUID.fromString(ruleIdStr);

    var rule = rules.get(ruleId);
    if (rule == null) return;

    var evaluator = current.evaluators.computeIfAbsent(ruleId, k -> new RuleEvaluator(rule, groupKey, UUID::randomUUID));
    var alerts = evaluator.onEvent(event);
    for (Alert a : alerts) {
      out.collect(alertSerializer.serializeToString(a));
    }

    stateMap.update(current);
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

  /** keyed state 의 직렬화 가능 wrapper. ValueState 가 한 번에 set 되도록. */
  public static class EvaluatorStateMap implements Serializable {
    public Map<UUID, RuleEvaluator> evaluators = new HashMap<>();
  }

  /** 사용되지 않는 import 제거 방지. */
  @SuppressWarnings("unused")
  private static void unused() {
    new ObjectMapper().registerModule(new JavaTimeModule());
  }
}
