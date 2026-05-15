package com.example.security.domain.sigma

import com.example.security.domain.common.Severity
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import com.example.security.domain.rule.AlertRule.RuleType
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.Objects
import java.util.UUID

/**
 * Sigma 룰 → 본 시스템의 [AlertRule] 변환기.
 *
 * Sigma 의 모든 표현이 우리 룰 DSL 로 1:1 매핑되지는 않는다. 본 매퍼는 다음을 지원한다.
 * - `logsource.category` / `logsource.product` → ECS event.category 매핑
 * - `detection.selection.<field>` → AlertRule.filterCategory / filterAction / filterOutcome
 *   (가능한 경우만; 못 매핑하면 그대로 유지하고 변환 한계 결과에 기록)
 * - `detection.condition` 의 단순 형태 (`selection`, `selection and not filter`) →
 *   AlertRule 활성화 / 비활성화 힌트
 * - `level` → [Severity] 매핑 + 5분 임계값 5회 default (운영자가 import 후 조정)
 *
 * 지원하지 않는 Sigma 표현 (timeframe / count / near / aggregation) 은
 * [MappingResult.unsupported] 에 기록하여 운영자가 별도로 Flink CEP 로 구현하도록 한다.
 *
 * Sigma field modifier 매핑 (예: `EventID|equals: 4625`) 도 일부만 지원한다 — equals /
 * contains / startswith / endswith 는 인식, 나머지는 unsupported 로 기록.
 */
class SigmaToAlertRuleMapper(
    private val defaultWindow: Duration,
    private val defaultThreshold: Int,
) {

    constructor() : this(DEFAULT_WINDOW, DEFAULT_THRESHOLD)

    fun map(sigma: SigmaRule, tenantId: TenantId, now: Instant): MappingResult {
        val unsupported = ArrayList<String>()

        val category = mapCategory(sigma.logsource, unsupported)
        val selection = primarySelection(sigma.detection, sigma.condition(), unsupported)

        var filterAction: String? = null
        var filterOutcome: String? = null
        var groupByField = "source.ip" // SOC default — 동일 IP 기준 임계값.

        if (selection != null) {
            for (entry in selection.entries) {
                val rawKey = entry.key
                // selection 은 raw map (Map<*,*>) 에서 cast 된 것이라 값이 null 일 수 있다 —
                // 원본 Java 와 동일하게 null 을 그대로 통과시킨다.
                val rawValue: Any? = entry.value
                val value = rawValue?.toString()
                val parsed = SigmaField.parse(rawKey)
                if (!parsed.isEqualsLike()) {
                    unsupported.add(
                        "field modifier 미지원: %s (현재 equals/contains/startswith/endswith 만)"
                            .format(rawKey),
                    )
                    continue
                }
                val ecs = SigmaFieldNameMap.toEcs(parsed.name)
                when (ecs) {
                    // event.code (Windows EventID 등) 는 알람 룰의 *trigger 식별자* 로 자연 매핑.
                    "event.action", "event.code" -> filterAction = value
                    "event.outcome" -> filterOutcome = value
                    "source.ip", "user.name", "host.hostname" -> groupByField = ecs
                    else -> {
                        // 룰 DSL 의 group/filter 키가 아닌 필드 — 운영자 검토 필요 → unsupported 기록.
                        unsupported.add(
                            "필드 미매핑: %s (Sigma 원본 키 %s)".format(parsed.name, rawKey),
                        )
                    }
                }
            }
        }

        val condition = sigma.condition()
        val conditionSimple = isSimpleCondition(condition)
        if (!conditionSimple) {
            unsupported.add("condition 미지원 (단순 selection 형태만): $condition")
        }
        if (containsAggregation(condition)) {
            unsupported.add("aggregation 미지원 (count/sum/avg/max/min): $condition")
        }
        if (sigma.detection.keys.any { it == "timeframe" }) {
            unsupported.add("timeframe 직접 매핑 미지원 — defaultWindow 로 대체")
        }

        // multi-selection 검출 — condition 이 detection 의 selection 키를 2개 이상 참조하면
        // primarySelection 이 첫 selection 만 사용하므로 다른 selection 의 매칭이 silent skip 된다.
        // 이 경우 false negative 가 되므로 unsupported 기록 + 룰은 disabled 로 import 된다.
        val referenced = referencedSelectionKeys(condition, sigma.detection.keys)
        if (referenced.size > 1) {
            unsupported.add(
                "condition 이 다중 selection 참조 — 첫 selection 만 매핑되어 나머지는 silent skip" +
                    ": " + condition + " (" + referenced + ")",
            )
        }

        val rule =
            AlertRule(
                UUID.randomUUID(),
                tenantId,
                truncated(sigma.title, 200)!!,
                sigmaDescription(sigma),
                RuleType.THRESHOLD,
                category,
                filterAction,
                filterOutcome,
                groupByField,
                defaultThreshold,
                defaultWindow,
                sigmaLevelToSeverity(sigma.level),
                // unsupported 가 비어있으면 enabled, 아니면 운영자 검토 필요 → disabled.
                unsupported.isEmpty(),
                now,
                now,
            )

        return MappingResult(rule, sigma, java.util.List.copyOf(unsupported))
    }

    /**
     * 변환 결과.
     *
     * @property rule 변환된 AlertRule (필요 시 운영자가 추가 조정)
     * @property source 원본 Sigma 룰
     * @property unsupported 변환에서 누락된 Sigma 표현 — 운영자가 인지하고 별도 Flink CEP /
     *   룰 DSL 확장으로 대응
     */
    class MappingResult(
        rule: AlertRule,
        source: SigmaRule,
        unsupported: List<String>?,
    ) {
        @get:JvmName("rule")
        val rule: AlertRule = rule

        @get:JvmName("source")
        val source: SigmaRule = source

        @get:JvmName("unsupported")
        val unsupported: List<String> =
            if (unsupported == null) emptyList() else java.util.List.copyOf(unsupported)

        fun fullySupported(): Boolean = unsupported.isEmpty()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MappingResult) return false
            return rule == other.rule &&
                source == other.source &&
                unsupported == other.unsupported
        }

        override fun hashCode(): Int = Objects.hash(rule, source, unsupported)

        override fun toString(): String =
            "MappingResult[rule=$rule, source=$source, unsupported=$unsupported]"
    }

    companion object {
        /** 변환 default — 운영자가 import 후 PUT /api/v1/alert-rules/{id} 로 조정. */
        private const val DEFAULT_THRESHOLD: Int = 5

        private val DEFAULT_WINDOW: Duration = Duration.ofMinutes(5)

        /** Sigma `level` → [Severity]. */
        private fun sigmaLevelToSeverity(level: String?): Severity {
            if (level == null) return Severity.MEDIUM
            return when (level.lowercase(Locale.ROOT)) {
                "informational", "info" -> Severity.INFO
                "low" -> Severity.LOW
                "medium" -> Severity.MEDIUM
                "high" -> Severity.HIGH
                "critical" -> Severity.CRITICAL
                else -> Severity.MEDIUM
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun primarySelection(
            detection: Map<String, Any>,
            condition: String?,
            unsupported: MutableList<String>,
        ): Map<String, Any>? {
            // Sigma 의 detection 안에는 condition + 1개 이상의 selection 키가 있다.
            // condition 이 정확히 1개 selection 만 참조하면 그 selection 을 결정성 있게 선택,
            // 그렇지 않으면 (참조 0개 / 다중 / unparseable) 첫 Map 엔트리로 fallback.
            val referenced = referencedSelectionKeys(condition, detection.keys)
            val preferredKey = if (referenced.size == 1) referenced.iterator().next() else null

            if (preferredKey != null) {
                val v = detection[preferredKey]
                if (v is Map<*, *>) {
                    return v as Map<String, Any>
                }
                if (v is List<*>) {
                    unsupported.add("list 형태 selection 미지원: $preferredKey")
                }
            }
            for (entry in detection.entries) {
                if ("condition" == entry.key || "timeframe" == entry.key) continue
                val value = entry.value
                if (value is Map<*, *>) {
                    return value as Map<String, Any>
                }
                if (value is List<*>) {
                    unsupported.add("list 형태 selection 미지원: ${entry.key}")
                }
            }
            return null
        }

        private fun mapCategory(
            logsource: Map<String, String>?,
            unsupported: MutableList<String>,
        ): String? {
            if (logsource.isNullOrEmpty()) return null
            val category = logsource["category"] ?: return null
            // Sigma logsource.category 는 sigma-specification 이 정의하는 표준 셋이다.
            // 본 매핑은 ECS event.category 와 1:1 정렬되는 항목만 지원.
            return when (category) {
                "authentication", "auth" -> "authentication"
                "process_creation", "process" -> "process"
                "file_event", "file_access", "file" -> "file"
                "network_connection", "dns", "proxy", "firewall" -> "network"
                "registry_event", "registry_set", "registry_add", "registry_delete" -> "registry"
                "webserver" -> "web"
                else -> {
                    unsupported.add("logsource.category 미매핑: $category")
                    null
                }
            }
        }

        /** condition 이 단순한 selection 1개 또는 selection AND/OR/NOT selection 형태인지. */
        private fun isSimpleCondition(condition: String?): Boolean {
            if (condition.isNullOrBlank()) return true
            val c = condition.lowercase(Locale.ROOT)
            if (c.contains("|")) return false // pipe — aggregation
            if (c.contains(" of ")) return false // 1 of selection*
            return true
        }

        /**
         * condition 문자열이 참조하는 detection key (selection / filter 등) 의 집합 추출.
         *
         * Sigma 의 condition 은 식별자 + AND/OR/NOT/괄호로 구성된다. 본 메서드는 condition
         * 안의 식별자 토큰 중 detection 의 키와 일치하는 것만 모은다 — 와일드카드
         * (`selection*`) 같은 표현은 무시 (이미 isSimpleCondition 이 " of " 로 차단).
         */
        private fun referencedSelectionKeys(
            condition: String?,
            detectionKeys: Set<String>,
        ): Set<String> {
            if (condition.isNullOrBlank()) return emptySet()
            // Java 의 String.split(regex) (limit 0) 와 동일하게 trailing empty 제거.
            val tokens =
                condition.lowercase(Locale.ROOT)
                    .split(Regex("[^a-z0-9_]+"))
                    .dropLastWhile { it.isEmpty() }
            val result = LinkedHashSet<String>()
            for (key in detectionKeys) {
                if ("condition" == key || "timeframe" == key) continue
                val lower = key.lowercase(Locale.ROOT)
                for (t in tokens) {
                    if (t == lower) {
                        result.add(key)
                        break
                    }
                }
            }
            return result
        }

        private fun containsAggregation(condition: String?): Boolean {
            if (condition == null) return false
            val c = condition.lowercase(Locale.ROOT)
            return c.contains("count(") ||
                c.contains("sum(") ||
                c.contains("avg(") ||
                c.contains("min(") ||
                c.contains("max(")
        }

        private fun sigmaDescription(sigma: SigmaRule): String? {
            val sb = StringBuilder()
            if (!sigma.description.isNullOrBlank()) {
                sb.append(sigma.description)
            }
            if (sigma.references.isNotEmpty()) {
                sb.append("\nrefs: ").append(sigma.references.joinToString(", "))
            }
            if (sigma.tags.isNotEmpty()) {
                sb.append("\ntags: ").append(sigma.tags.joinToString(", "))
            }
            // sigma.id 는 non-null 이지만 원본 Java 의 분기 구조를 그대로 둔다.
            sb.append("\nsigma_id: ").append(sigma.id)
            return truncated(sb.toString(), 1000)
        }

        private fun truncated(s: String?, max: Int): String? {
            if (s == null) return null
            return if (s.length <= max) s else s.substring(0, max)
        }
    }
}
