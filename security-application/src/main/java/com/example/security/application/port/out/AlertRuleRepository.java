package com.example.security.application.port.out;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 알람 룰 영속 — Postgres alert_rules 테이블. */
public interface AlertRuleRepository {

  AlertRule save(AlertRule rule);

  Optional<AlertRule> findById(UUID ruleId);

  List<AlertRule> findEnabledByTenant(TenantId tenantId);

  List<AlertRule> findAllEnabled();

  void deleteById(UUID ruleId);
}
