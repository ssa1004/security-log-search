package com.example.security.adapter.in.rest.dto;

import com.example.security.domain.rule.Alert;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertResponse(
    UUID alertId,
    String tenantId,
    UUID ruleId,
    String ruleName,
    String severity,
    String groupKey,
    String groupByField,
    int matchedCount,
    Instant windowStart,
    Instant windowEnd,
    Instant firedAt,
    String status,
    List<UUID> triggeringEventIds,
    String message) {

  public static AlertResponse from(Alert a) {
    return new AlertResponse(
        a.alertId(),
        a.tenantId().value(),
        a.ruleId(),
        a.ruleName(),
        a.severity().name(),
        a.groupKey(),
        a.groupByField(),
        a.matchedCount(),
        a.windowStart(),
        a.windowEnd(),
        a.firedAt(),
        a.status().name(),
        a.triggeringEventIds(),
        a.message());
  }
}
