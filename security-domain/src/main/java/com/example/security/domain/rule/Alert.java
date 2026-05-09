package com.example.security.domain.rule;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 발화된 알람 — 룰이 매칭되어 Flink job 이 emit 한 결과.
 *
 * <p>{@code triggeringEventIds} 는 본 알람을 트리거한 원본 LogEvent 들의 id 목록 (수사관이
 * drill-down 할 때 사용). 보존 정책상 ID 만 보관, 본문은 OpenSearch 에서 lookup.
 */
public record Alert(
    UUID alertId,
    TenantId tenantId,
    UUID ruleId,
    String ruleName,
    Severity severity,
    /** 룰의 그룹 키 값 — 예: 192.168.1.10 (source.ip 룰의 경우). */
    String groupKey,
    String groupByField,
    int matchedCount,
    Instant windowStart,
    Instant windowEnd,
    Instant firedAt,
    AlertStatus status,
    List<UUID> triggeringEventIds,
    String message) implements java.io.Serializable {

  public Alert {
    Objects.requireNonNull(alertId);
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(ruleId);
    Objects.requireNonNull(ruleName);
    Objects.requireNonNull(severity);
    Objects.requireNonNull(groupByField);
    Objects.requireNonNull(windowStart);
    Objects.requireNonNull(windowEnd);
    Objects.requireNonNull(firedAt);
    Objects.requireNonNull(status);
    triggeringEventIds = triggeringEventIds == null ? List.of() : List.copyOf(triggeringEventIds);
  }

  public Alert acknowledge() {
    return new Alert(
        alertId,
        tenantId,
        ruleId,
        ruleName,
        severity,
        groupKey,
        groupByField,
        matchedCount,
        windowStart,
        windowEnd,
        firedAt,
        AlertStatus.ACKNOWLEDGED,
        triggeringEventIds,
        message);
  }

  public Alert resolve() {
    return new Alert(
        alertId,
        tenantId,
        ruleId,
        ruleName,
        severity,
        groupKey,
        groupByField,
        matchedCount,
        windowStart,
        windowEnd,
        firedAt,
        AlertStatus.RESOLVED,
        triggeringEventIds,
        message);
  }

  public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    FALSE_POSITIVE
  }
}
