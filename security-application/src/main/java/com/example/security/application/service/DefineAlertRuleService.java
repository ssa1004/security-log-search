package com.example.security.application.service;

import com.example.security.application.exception.RuleNotFoundException;
import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.DefineAlertRuleUseCase;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.out.AlertRuleRepository;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.rule.AlertRule;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** use case 4 — 알람 룰 CRUD. */
@Service
public class DefineAlertRuleService implements DefineAlertRuleUseCase {

  private final AlertRuleRepository repo;
  private final AuditLogPort audit;
  private final Clock clock;

  public DefineAlertRuleService(AlertRuleRepository repo, AuditLogPort audit, Clock clock) {
    this.repo = repo;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AlertRule create(CreateRuleCommand cmd, OperatorContext operator) {
    enforceTenant(operator, cmd.tenantId());
    var now = clock.instant();
    var rule =
        new AlertRule(
            UUID.randomUUID(),
            cmd.tenantId(),
            cmd.name(),
            cmd.description(),
            cmd.type(),
            cmd.filterCategory(),
            cmd.filterAction(),
            cmd.filterOutcome(),
            cmd.groupByField(),
            cmd.threshold(),
            cmd.window(),
            cmd.severity(),
            cmd.enabled(),
            now,
            now);
    var saved = repo.save(rule);
    auditChange(saved, operator, AuditAction.RULE_CREATED);
    return saved;
  }

  @Override
  @Transactional
  public AlertRule update(UUID ruleId, CreateRuleCommand cmd, OperatorContext operator) {
    var existing = repo.findById(ruleId).orElseThrow(() -> new RuleNotFoundException(ruleId));
    enforceTenant(operator, existing.tenantId());
    var updated =
        new AlertRule(
            existing.ruleId(),
            existing.tenantId(),
            cmd.name(),
            cmd.description(),
            cmd.type(),
            cmd.filterCategory(),
            cmd.filterAction(),
            cmd.filterOutcome(),
            cmd.groupByField(),
            cmd.threshold(),
            cmd.window(),
            cmd.severity(),
            cmd.enabled(),
            existing.createdAt(),
            clock.instant());
    var saved = repo.save(updated);
    auditChange(saved, operator, AuditAction.RULE_UPDATED);
    return saved;
  }

  @Override
  @Transactional
  public void delete(UUID ruleId, OperatorContext operator) {
    var existing = repo.findById(ruleId).orElseThrow(() -> new RuleNotFoundException(ruleId));
    enforceTenant(operator, existing.tenantId());
    repo.deleteById(ruleId);
    auditChange(existing, operator, AuditAction.RULE_DELETED);
  }

  private void enforceTenant(OperatorContext operator, com.example.security.domain.common.TenantId tenant) {
    if (!operator.canQueryOtherTenant() && !operator.tenantId().equals(tenant)) {
      throw new TenantMismatchException(operator.tenantId(), tenant);
    }
    CrossTenantAccessAudit.recordIfCrossTenant(
        audit, clock, operator, tenant, "alert_rule", tenant.value());
  }

  private void auditChange(AlertRule rule, OperatorContext operator, AuditAction action) {
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            rule.tenantId(),
            clock.instant(),
            operator.subject(),
            operator.roles().stream().map(Enum::name).collect(Collectors.joining(",")),
            action,
            "alert_rule",
            rule.ruleId().toString(),
            operator.sourceIp(),
            Map.of(
                "name", rule.name(),
                "type", rule.type().name(),
                "threshold", Integer.toString(rule.threshold()),
                "window", rule.window().toString(),
                "enabled", Boolean.toString(rule.enabled()))));
  }
}
