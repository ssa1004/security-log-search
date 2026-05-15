package com.example.security.application.service;

import com.example.security.application.exception.AlertNotFoundException;
import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.ListAlertsUseCase;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.out.AlertRepository;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.rule.Alert;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** use case 6 — 발화된 알람 timeline + 운영자 처리. */
@Service
public class ListAlertsService implements ListAlertsUseCase {

  private final AlertRepository alerts;
  private final AuditLogPort audit;
  private final Clock clock;

  public ListAlertsService(AlertRepository alerts, AuditLogPort audit, Clock clock) {
    this.alerts = alerts;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public Page list(ListAlertsQuery query, OperatorContext operator) {
    enforceTenant(operator, query.tenantId());
    var fetched = alerts.query(query);
    UUID nextCursor =
        fetched.size() == query.size() ? fetched.get(fetched.size() - 1).alertId() : null;
    return new Page(fetched, nextCursor);
  }

  @Override
  @Transactional
  public Alert acknowledge(UUID alertId, OperatorContext operator) {
    return transition(alertId, operator, Alert::acknowledge, AuditAction.ALERT_ACKNOWLEDGED);
  }

  @Override
  @Transactional
  public Alert resolve(UUID alertId, OperatorContext operator) {
    return transition(alertId, operator, Alert::resolve, AuditAction.ALERT_RESOLVED);
  }

  @Override
  @Transactional
  public Alert markFalsePositive(UUID alertId, OperatorContext operator) {
    return transition(alertId, operator, this::falsePositive, AuditAction.ALERT_FALSE_POSITIVE);
  }

  private Alert falsePositive(Alert a) {
    return new Alert(
        a.alertId(),
        a.tenantId(),
        a.ruleId(),
        a.ruleName(),
        a.severity(),
        a.groupKey(),
        a.groupByField(),
        a.matchedCount(),
        a.windowStart(),
        a.windowEnd(),
        a.firedAt(),
        Alert.AlertStatus.FALSE_POSITIVE,
        a.triggeringEventIds(),
        a.message());
  }

  private Alert transition(
      UUID alertId,
      OperatorContext operator,
      java.util.function.Function<Alert, Alert> transition,
      AuditAction action) {
    var existing = alerts.findById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
    enforceTenant(operator, existing.tenantId());
    var updated = transition.apply(existing);
    var saved = alerts.save(updated);
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            saved.tenantId(),
            clock.instant(),
            operator.subject(),
            operator.roles().stream().map(Enum::name).collect(Collectors.joining(",")),
            action,
            "alert",
            saved.alertId().toString(),
            operator.sourceIp(),
            Map.of("status", saved.status().name(), "rule", saved.ruleName())));
    return saved;
  }

  private void enforceTenant(OperatorContext operator, com.example.security.domain.common.TenantId tenant) {
    if (!operator.canQueryOtherTenant() && !operator.tenantId().equals(tenant)) {
      throw new TenantMismatchException(operator.tenantId(), tenant);
    }
    CrossTenantAccessAudit.recordIfCrossTenant(audit, clock, operator, tenant, "alert", tenant.value());
  }
}
