package com.example.security.domain.sigma;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sigma 룰 → 본 시스템의 {@link AlertRule} 변환기.
 *
 * <p>Sigma 의 모든 표현이 우리 룰 DSL 로 1:1 매핑되지는 않는다. 본 매퍼는 다음을 지원한다.
 *
 * <ul>
 *   <li>{@code logsource.category} / {@code logsource.product} → ECS event.category 매핑
 *   <li>{@code detection.selection.<field>} → AlertRule.filterCategory / filterAction /
 *       filterOutcome (가능한 경우만; 못 매핑하면 그대로 유지하고 변환 한계 결과에 기록)
 *   <li>{@code detection.condition} 의 단순 형태 ({@code selection}, {@code selection and not
 *       filter}) → AlertRule 활성화 / 비활성화 힌트
 *   <li>{@code level} → {@link Severity} 매핑 + 5분 임계값 5회 default (운영자가 import 후 조정)
 * </ul>
 *
 * <p>지원하지 않는 Sigma 표현 (timeframe / count / near / aggregation) 은 {@link
 * MappingResult#unsupported()} 에 기록하여 운영자가 별도로 Flink CEP 로 구현하도록 한다.
 *
 * <p>Sigma field modifier 매핑 (예: {@code EventID|equals: 4625}) 도 일부만 지원한다 — equals /
 * contains / startswith / endswith 는 인식, 나머지는 unsupported 로 기록.
 */
public class SigmaToAlertRuleMapper {

  /** 변환 default — 운영자가 import 후 PUT /api/v1/alert-rules/{id} 로 조정. */
  static final int DEFAULT_THRESHOLD = 5;

  static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

  private final Duration defaultWindow;
  private final int defaultThreshold;

  public SigmaToAlertRuleMapper() {
    this(DEFAULT_WINDOW, DEFAULT_THRESHOLD);
  }

  public SigmaToAlertRuleMapper(Duration defaultWindow, int defaultThreshold) {
    this.defaultWindow = Objects.requireNonNull(defaultWindow);
    this.defaultThreshold = defaultThreshold;
  }

  public MappingResult map(SigmaRule sigma, TenantId tenantId, Instant now) {
    Objects.requireNonNull(sigma);
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(now);

    var unsupported = new java.util.ArrayList<String>();

    var category = mapCategory(sigma.logsource(), unsupported);
    var selection = primarySelection(sigma.detection(), unsupported);

    String filterAction = null;
    String filterOutcome = null;
    String groupByField = "source.ip"; // SOC default — 동일 IP 기준 임계값.

    if (selection != null) {
      for (var entry : selection.entrySet()) {
        var rawKey = entry.getKey();
        var value = entry.getValue() == null ? null : entry.getValue().toString();
        var parsed = SigmaField.parse(rawKey);
        if (!parsed.isEqualsLike()) {
          unsupported.add(
              "field modifier 미지원: %s (현재 equals/contains/startswith/endswith 만)"
                  .formatted(rawKey));
          continue;
        }
        var ecs = SigmaFieldNameMap.toEcs(parsed.name());
        switch (ecs) {
          // event.code (Windows EventID 등) 는 알람 룰의 *trigger 식별자* 로 자연 매핑.
          case "event.action", "event.code" -> filterAction = value;
          case "event.outcome" -> filterOutcome = value;
          case "source.ip", "user.name", "host.hostname" -> groupByField = ecs;
          default -> {
            // 룰 DSL 의 group/filter 키가 아닌 필드 — 운영자 검토 필요 → unsupported 기록.
            unsupported.add("필드 미매핑: %s (Sigma 원본 키 %s)".formatted(parsed.name(), rawKey));
          }
        }
      }
    }

    var condition = sigma.condition();
    boolean conditionSimple = isSimpleCondition(condition);
    if (!conditionSimple) {
      unsupported.add("condition 미지원 (단순 selection 형태만): " + condition);
    }
    if (containsAggregation(condition)) {
      unsupported.add("aggregation 미지원 (count/sum/avg/max/min): " + condition);
    }
    if (sigma.detection().keySet().stream().anyMatch(k -> k.equals("timeframe"))) {
      unsupported.add("timeframe 직접 매핑 미지원 — defaultWindow 로 대체");
    }

    var rule =
        new AlertRule(
            UUID.randomUUID(),
            tenantId,
            truncated(sigma.title(), 200),
            sigmaDescription(sigma),
            RuleType.THRESHOLD,
            category,
            filterAction,
            filterOutcome,
            groupByField,
            defaultThreshold,
            defaultWindow,
            sigmaLevelToSeverity(sigma.level()),
            // unsupported 가 비어있으면 enabled, 아니면 운영자 검토 필요 → disabled.
            unsupported.isEmpty(),
            now,
            now);

    return new MappingResult(rule, sigma, java.util.List.copyOf(unsupported));
  }

  /** Sigma {@code level} → {@link Severity}. */
  static Severity sigmaLevelToSeverity(String level) {
    if (level == null) return Severity.MEDIUM;
    return switch (level.toLowerCase(Locale.ROOT)) {
      case "informational", "info" -> Severity.INFO;
      case "low" -> Severity.LOW;
      case "medium" -> Severity.MEDIUM;
      case "high" -> Severity.HIGH;
      case "critical" -> Severity.CRITICAL;
      default -> Severity.MEDIUM;
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> primarySelection(
      Map<String, Object> detection, java.util.List<String> unsupported) {
    // Sigma 의 detection 안에는 condition + 1개 이상의 selection 키가 있다.
    // 본 매퍼는 첫 selection-like 맵을 1차 매칭 셀로 사용한다.
    for (var entry : detection.entrySet()) {
      if ("condition".equals(entry.getKey()) || "timeframe".equals(entry.getKey())) continue;
      if (entry.getValue() instanceof Map<?, ?> m) {
        return (Map<String, Object>) m;
      }
      if (entry.getValue() instanceof java.util.List<?>) {
        unsupported.add("list 형태 selection 미지원: " + entry.getKey());
      }
    }
    return null;
  }

  private static String mapCategory(Map<String, String> logsource, java.util.List<String> unsupported) {
    if (logsource == null || logsource.isEmpty()) return null;
    var category = logsource.get("category");
    if (category == null) return null;
    // Sigma logsource.category 는 sigma-specification 이 정의하는 표준 셋이다.
    // 본 매핑은 ECS event.category 와 1:1 정렬되는 항목만 지원.
    return switch (category) {
      case "authentication", "auth" -> "authentication";
      case "process_creation", "process" -> "process";
      case "file_event", "file_access", "file" -> "file";
      case "network_connection", "dns", "proxy", "firewall" -> "network";
      case "registry_event", "registry_set", "registry_add", "registry_delete" -> "registry";
      case "webserver" -> "web";
      default -> {
        unsupported.add("logsource.category 미매핑: " + category);
        yield null;
      }
    };
  }

  /** condition 이 단순한 selection 1개 또는 selection AND/OR/NOT selection 형태인지. */
  static boolean isSimpleCondition(String condition) {
    if (condition == null || condition.isBlank()) return true;
    var c = condition.toLowerCase(Locale.ROOT);
    if (c.contains("|")) return false; // pipe — aggregation
    if (c.contains(" of ")) return false; // 1 of selection*
    return true;
  }

  static boolean containsAggregation(String condition) {
    if (condition == null) return false;
    var c = condition.toLowerCase(Locale.ROOT);
    return c.contains("count(") || c.contains("sum(") || c.contains("avg(") || c.contains("min(")
        || c.contains("max(");
  }

  private static String sigmaDescription(SigmaRule sigma) {
    var sb = new StringBuilder();
    if (sigma.description() != null && !sigma.description().isBlank()) {
      sb.append(sigma.description());
    }
    if (!sigma.references().isEmpty()) {
      sb.append("\nrefs: ").append(String.join(", ", sigma.references()));
    }
    if (!sigma.tags().isEmpty()) {
      sb.append("\ntags: ").append(String.join(", ", sigma.tags()));
    }
    if (sigma.id() != null) {
      sb.append("\nsigma_id: ").append(sigma.id());
    }
    return truncated(sb.toString(), 1000);
  }

  private static String truncated(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }

  /**
   * 변환 결과.
   *
   * @param rule 변환된 AlertRule (필요 시 운영자가 추가 조정)
   * @param source 원본 Sigma 룰
   * @param unsupported 변환에서 누락된 Sigma 표현 — 운영자가 인지하고 별도 Flink CEP / 룰 DSL 확장으로 대응
   */
  public record MappingResult(AlertRule rule, SigmaRule source, java.util.List<String> unsupported) {

    public MappingResult {
      Objects.requireNonNull(rule);
      Objects.requireNonNull(source);
      unsupported = unsupported == null ? java.util.List.of() : java.util.List.copyOf(unsupported);
    }

    public boolean fullySupported() {
      return unsupported.isEmpty();
    }
  }
}
