package com.example.security.adapter.out.jpa;

import com.example.security.adapter.out.jpa.entity.AlertRuleEntity;
import com.example.security.adapter.out.jpa.repository.AlertRuleJpaRepository;
import com.example.security.application.port.out.AlertRuleRepository;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.rule.AlertRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JpaAlertRuleRepository implements AlertRuleRepository {

  private final AlertRuleJpaRepository jpa;

  public JpaAlertRuleRepository(AlertRuleJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AlertRule save(AlertRule rule) {
    return jpa.save(AlertRuleEntity.from(rule)).toDomain();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AlertRule> findById(UUID ruleId) {
    return jpa.findById(ruleId).map(AlertRuleEntity::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AlertRule> findEnabledByTenant(TenantId tenantId) {
    return jpa.findByTenantIdAndEnabledTrue(tenantId.value()).stream()
        .map(AlertRuleEntity::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<AlertRule> findAllEnabled() {
    return jpa.findByEnabledTrue().stream().map(AlertRuleEntity::toDomain).toList();
  }

  @Override
  public void deleteById(UUID ruleId) {
    jpa.deleteById(ruleId);
  }
}
