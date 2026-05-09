package com.example.security.application.port.in;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import com.example.security.domain.rule.AlertRule.RuleType;
import java.time.Duration;
import java.util.UUID;

/**
 * use case 4 — 운영자가 알람 룰 CRUD.
 *
 * <p>Postgres 영속 + Flink 의 broadcast state 로 hot reload (룰 추가 / 변경 시 Flink job 재시작
 * 불필요).
 */
public interface DefineAlertRuleUseCase {

  AlertRule create(CreateRuleCommand command, OperatorContext operator);

  AlertRule update(UUID ruleId, CreateRuleCommand command, OperatorContext operator);

  void delete(UUID ruleId, OperatorContext operator);

  record CreateRuleCommand(
      TenantId tenantId,
      String name,
      String description,
      RuleType type,
      String filterCategory,
      String filterAction,
      String filterOutcome,
      String groupByField,
      int threshold,
      Duration window,
      Severity severity,
      boolean enabled) {}
}
