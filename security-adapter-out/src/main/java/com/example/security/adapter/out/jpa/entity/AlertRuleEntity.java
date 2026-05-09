package com.example.security.adapter.out.jpa.entity;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** alert_rules 테이블. */
@Entity
@Table(
    name = "alert_rules",
    indexes = {
      @Index(name = "ix_alert_rules_tenant_enabled", columnList = "tenant_id,enabled")
    })
public class AlertRuleEntity {

  @Id
  @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @Column(name = "tenant_id", nullable = false, length = 32)
  private String tenantId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private RuleType type;

  @Column(name = "filter_category", length = 64)
  private String filterCategory;

  @Column(name = "filter_action", length = 64)
  private String filterAction;

  @Column(name = "filter_outcome", length = 16)
  private String filterOutcome;

  @Column(name = "group_by_field", nullable = false, length = 64)
  private String groupByField;

  @Column(name = "threshold", nullable = false)
  private int threshold;

  @Column(name = "window_seconds", nullable = false)
  private long windowSeconds;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Severity severity;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AlertRuleEntity() {}

  public static AlertRuleEntity from(AlertRule rule) {
    var e = new AlertRuleEntity();
    e.ruleId = rule.ruleId();
    e.tenantId = rule.tenantId().value();
    e.name = rule.name();
    e.description = rule.description();
    e.type = rule.type();
    e.filterCategory = rule.filterCategory();
    e.filterAction = rule.filterAction();
    e.filterOutcome = rule.filterOutcome();
    e.groupByField = rule.groupByField();
    e.threshold = rule.threshold();
    e.windowSeconds = rule.window().toSeconds();
    e.severity = rule.severity();
    e.enabled = rule.enabled();
    e.createdAt = rule.createdAt();
    e.updatedAt = rule.updatedAt();
    return e;
  }

  public AlertRule toDomain() {
    return new AlertRule(
        ruleId,
        TenantId.of(tenantId),
        name,
        description,
        type,
        filterCategory,
        filterAction,
        filterOutcome,
        groupByField,
        threshold,
        Duration.ofSeconds(windowSeconds),
        severity,
        enabled,
        createdAt,
        updatedAt);
  }

  public UUID getRuleId() {
    return ruleId;
  }
}
