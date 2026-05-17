package com.example.security.streaming.operator

import com.example.security.domain.event.LogEvent
import com.example.security.domain.rule.AlertRule
import com.example.security.streaming.serde.AlertJsonSerializer
import java.io.Serializable
import java.util.HashMap
import java.util.UUID
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeHint
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction
import org.apache.flink.util.Collector

/**
 * Flink 의 핵심 operator — broadcast 된 룰을 받아 keyed event stream 에 적용한다.
 *
 * 키 디자인:
 * - 이벤트 stream 의 key = tenantId — 룰 / 그룹 키는 keyBy 시점에 알 수 없다 (룰은 broadcast
 *   채널로만 들어옴). 한 tenant 의 모든 룰 / 그룹 키를 같은 operator 에서 처리.
 * - 룰 broadcast — 모든 키에서 룰 변경을 즉시 반영 (hot reload, ADR-0008)
 *
 * 운영 시 룰 전체를 broadcast 한다 (룰 수가 < 수천 개 가정). `processElement` 가 broadcast 된 룰
 * 전체를 순회하며 각 룰의 그룹 키별 [RuleEvaluator] state 를 보관 →
 * `(ruleId|groupKey) -> RuleEvaluator` 맵을 ValueState 로 직렬화.
 *
 * 제약:
 * - 본 구현은 LocalExecutionEnvironment 에서 단위 테스트되도록 설계 (직렬화 가능)
 * - 운영 클러스터에서는 RocksDB state backend 권장
 */
open class CorrelationProcessFunction :
    KeyedBroadcastProcessFunction<String, LogEvent, AlertRule, String>() {

    /** keyed state — (ruleId|groupKey) 단위 evaluator 상태. ValueState 로 한 번에 묶어서 보관. */
    @Transient
    private var stateMap: ValueState<EvaluatorStateMap>? = null

    @Transient
    private var alertSerializer: AlertJsonSerializer? = null

    override fun open(parameters: Configuration) {
        val desc = ValueStateDescriptor("eval-state", EvaluatorStateMap::class.java)
        stateMap = runtimeContext.getState(desc)
        alertSerializer = AlertJsonSerializer()
    }

    override fun processElement(
        event: LogEvent,
        ctx: ReadOnlyContext,
        out: Collector<String>,
    ) {
        val rules = ctx.getBroadcastState(RULES_DESCRIPTOR)
        var current = stateMap!!.value()
        if (current == null) current = EvaluatorStateMap()

        // 스트림은 tenantId 로만 keyBy 되어 있다 — 이 tenant 의 broadcast 룰 전체를 순회하며
        // 룰별 그룹 키 단위로 evaluator state 를 적용한다. 룰 / 그룹 키는 keyBy 시점에 알 수 없어
        // (룰이 broadcast 로만 들어옴) 여기서 fan-out 한다.
        val tenant = event.tenantId
        var changed = false
        for (entry in rules.immutableEntries()) {
            val rule = entry.value
            if (rule == null || rule.tenantId != tenant) continue

            val groupKey = rule.extractGroupKey(event)
            val stateKey = stateKey(rule.ruleId, groupKey)
            val evaluator =
                current.evaluators.computeIfAbsent(stateKey) {
                    RuleEvaluator(rule, groupKey, java.util.function.Supplier { UUID.randomUUID() })
                }
            val alerts = evaluator.onEvent(event)
            for (a in alerts) {
                out.collect(alertSerializer!!.serializeToString(a))
            }
            changed = true
        }

        if (changed) {
            stateMap!!.update(current)
        }
    }

    override fun processBroadcastElement(
        rule: AlertRule,
        ctx: Context,
        out: Collector<String>,
    ) {
        val rules = ctx.getBroadcastState(RULES_DESCRIPTOR)
        if (!rule.enabled) {
            rules.remove(rule.ruleId)
        } else {
            rules.put(rule.ruleId, rule)
        }
    }

    /**
     * keyed state 의 직렬화 가능 wrapper. ValueState 가 한 번에 set 되도록.
     *
     * 키는 [stateKey] 의 `ruleId|groupKey` 복합 키. Flink Kryo / POJO serializer 가
     * public field 와 default constructor 를 통해 직렬화하도록 일반 class 로 둔다.
     */
    class EvaluatorStateMap : Serializable {
        @JvmField
        var evaluators: MutableMap<String, RuleEvaluator> = HashMap()

        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    companion object {
        /** broadcast state 키: ruleId. */
        @JvmField
        val RULES_DESCRIPTOR: MapStateDescriptor<UUID, AlertRule> =
            MapStateDescriptor(
                "alert-rules",
                TypeInformation.of(object : TypeHint<UUID>() {}),
                TypeInformation.of(object : TypeHint<AlertRule>() {}),
            )

        /** keyed state 맵의 복합 키 — 한 tenant 안에서 (ruleId, groupKey) 조합을 구분. */
        @JvmStatic
        fun stateKey(ruleId: UUID, groupKey: String): String = "$ruleId|$groupKey"
    }
}
