package com.example.security.adapter.out.jpa.entity;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.Alert;
import com.example.security.domain.rule.Alert.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** alerts 테이블 — 발화된 알람. */
@Entity
@Table(
    name = "alerts",
    indexes = {
      @Index(name = "ix_alerts_tenant_fired", columnList = "tenant_id,fired_at"),
      @Index(name = "ix_alerts_status", columnList = "status")
    })
public class AlertEntity {

  @Id
  @Column(name = "alert_id", nullable = false)
  private UUID alertId;

  @Column(name = "tenant_id", nullable = false, length = 32)
  private String tenantId;

  @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @Column(name = "rule_name", nullable = false)
  private String ruleName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Severity severity;

  @Column(name = "group_key", nullable = false, length = 256)
  private String groupKey;

  @Column(name = "group_by_field", nullable = false, length = 64)
  private String groupByField;

  @Column(name = "matched_count", nullable = false)
  private int matchedCount;

  @Column(name = "window_start", nullable = false)
  private Instant windowStart;

  @Column(name = "window_end", nullable = false)
  private Instant windowEnd;

  @Column(name = "fired_at", nullable = false)
  private Instant firedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AlertStatus status;

  /** UUID 들을 콤마 join 으로 저장 — 별도 join 테이블 안 만든다 (조회량 적음). */
  @Column(name = "triggering_event_ids", length = 4000)
  private String triggeringEventIds;

  @Column(length = 1000)
  private String message;

  protected AlertEntity() {}

  public static AlertEntity from(Alert alert) {
    var e = new AlertEntity();
    e.alertId = alert.alertId();
    e.tenantId = alert.tenantId().value();
    e.ruleId = alert.ruleId();
    e.ruleName = alert.ruleName();
    e.severity = alert.severity();
    e.groupKey = alert.groupKey();
    e.groupByField = alert.groupByField();
    e.matchedCount = alert.matchedCount();
    e.windowStart = alert.windowStart();
    e.windowEnd = alert.windowEnd();
    e.firedAt = alert.firedAt();
    e.status = alert.status();
    e.triggeringEventIds =
        alert.triggeringEventIds().stream().map(UUID::toString).collect(Collectors.joining(","));
    e.message = alert.message();
    return e;
  }

  public Alert toDomain() {
    List<UUID> ids =
        triggeringEventIds == null || triggeringEventIds.isBlank()
            ? List.of()
            : Arrays.stream(triggeringEventIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .toList();
    return new Alert(
        alertId,
        TenantId.of(tenantId),
        ruleId,
        ruleName,
        severity,
        groupKey,
        groupByField,
        matchedCount,
        windowStart,
        windowEnd,
        firedAt,
        status,
        ids,
        message);
  }
}
