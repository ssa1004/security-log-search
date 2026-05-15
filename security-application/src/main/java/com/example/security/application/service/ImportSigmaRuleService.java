package com.example.security.application.service;

import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.ImportSigmaRuleUseCase;
import com.example.security.application.port.in.ListImportedSigmaRulesUseCase;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.out.AlertRuleRepository;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.SigmaRuleRepository;
import com.example.security.application.sigma.SigmaYamlParser;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.sigma.SigmaToAlertRuleMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * use case 9, 10 — Sigma 룰 import / 조회.
 *
 * <p>처리 순서 (importYaml):
 *
 * <ol>
 *   <li>operator → tenant 권한 검증
 *   <li>YAML parse (SnakeYAML SafeConstructor)
 *   <li>각 SigmaRule → AlertRule 변환 ({@link SigmaToAlertRuleMapper})
 *   <li>기존 sigma_id 가 있으면 update 모드로 (overwriteByTitle 옵션) 또는 skip
 *   <li>AlertRule 저장 + Sigma 원본 저장 + audit
 * </ol>
 */
@Service
public class ImportSigmaRuleService
    implements ImportSigmaRuleUseCase, ListImportedSigmaRulesUseCase {

  private static final Logger log = LoggerFactory.getLogger(ImportSigmaRuleService.class);

  private final SigmaRuleRepository sigmaRepo;
  private final AlertRuleRepository alertRuleRepo;
  private final SigmaYamlParser parser;
  private final SigmaToAlertRuleMapper mapper;
  private final AuditLogPort audit;
  private final Clock clock;

  public ImportSigmaRuleService(
      SigmaRuleRepository sigmaRepo,
      AlertRuleRepository alertRuleRepo,
      SigmaYamlParser parser,
      SigmaToAlertRuleMapper mapper,
      AuditLogPort audit,
      Clock clock) {
    this.sigmaRepo = sigmaRepo;
    this.alertRuleRepo = alertRuleRepo;
    this.parser = parser;
    this.mapper = mapper;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ImportResult importYaml(ImportCommand command, OperatorContext operator) {
    enforceTenant(operator, command.tenantId());
    var sigmaList = parser.parseAll(command.yaml());
    if (sigmaList.isEmpty()) {
      return new ImportResult(java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    var createdRules = new ArrayList<AlertRule>(sigmaList.size());
    var notes = new ArrayList<MappingNote>(sigmaList.size());
    var savedSigma = new ArrayList<com.example.security.domain.sigma.SigmaRule>(sigmaList.size());

    for (var sigma : sigmaList) {
      var existingRuleId = sigmaRepo.findAlertRuleId(sigma.id(), command.tenantId());
      if (existingRuleId.isPresent() && !command.overwriteByTitle()) {
        log.info("Sigma 룰 {} 는 이미 import 됨, skip", sigma.id());
        continue;
      }

      var mapping = mapper.map(sigma, command.tenantId(), clock.instant());
      AlertRule rule;
      if (existingRuleId.isPresent()) {
        // 기존 alert_rule 의 id 를 유지하고 내용을 덮어씀.
        var withSameId =
            new AlertRule(
                existingRuleId.get(),
                mapping.rule().tenantId(),
                mapping.rule().name(),
                mapping.rule().description(),
                mapping.rule().type(),
                mapping.rule().filterCategory(),
                mapping.rule().filterAction(),
                mapping.rule().filterOutcome(),
                mapping.rule().groupByField(),
                mapping.rule().threshold(),
                mapping.rule().window(),
                mapping.rule().severity(),
                mapping.rule().enabled(),
                mapping.rule().createdAt(),
                clock.instant());
        rule = alertRuleRepo.save(withSameId);
      } else {
        rule = alertRuleRepo.save(mapping.rule());
      }
      var saved = sigmaRepo.save(sigma, command.tenantId(), rule.ruleId());

      createdRules.add(rule);
      savedSigma.add(saved);
      notes.add(new MappingNote(rule.ruleId(), sigma.id(), mapping.unsupported()));

      audit.append(
          new AuditEntry(
              UUID.randomUUID(),
              command.tenantId(),
              clock.instant(),
              operator.subject(),
              operator.roles().stream().map(Enum::name).collect(Collectors.joining(",")),
              AuditAction.RULE_CREATED,
              "sigma_rule",
              sigma.id(),
              operator.sourceIp(),
              Map.of(
                  "title", sigma.title(),
                  "level", sigma.level() == null ? "" : sigma.level(),
                  "alert_rule_id", rule.ruleId().toString(),
                  "unsupported_count", Integer.toString(mapping.unsupported().size()))));
    }

    return new ImportResult(
        java.util.List.copyOf(createdRules),
        java.util.List.copyOf(savedSigma),
        java.util.List.copyOf(notes));
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.List<com.example.security.domain.sigma.SigmaRule> listByTenant(
      TenantId tenantId, OperatorContext operator) {
    enforceTenant(operator, tenantId);
    return sigmaRepo.findByTenant(tenantId);
  }

  private void enforceTenant(OperatorContext operator, TenantId tenant) {
    if (!operator.canQueryOtherTenant() && !operator.tenantId().equals(tenant)) {
      throw new TenantMismatchException(operator.tenantId(), tenant);
    }
    CrossTenantAccessAudit.recordIfCrossTenant(
        audit, clock, operator, tenant, "sigma_rule", tenant.value());
  }
}
