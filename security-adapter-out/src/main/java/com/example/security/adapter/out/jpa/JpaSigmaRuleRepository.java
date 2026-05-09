package com.example.security.adapter.out.jpa;

import com.example.security.adapter.out.jpa.entity.SigmaRuleEntity;
import com.example.security.adapter.out.jpa.repository.SigmaRuleJpaRepository;
import com.example.security.application.port.out.SigmaRuleRepository;
import com.example.security.application.sigma.SigmaYamlParser;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.sigma.SigmaRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JpaSigmaRuleRepository implements SigmaRuleRepository {

  private final SigmaRuleJpaRepository jpa;
  private final SigmaYamlParser parser;

  public JpaSigmaRuleRepository(SigmaRuleJpaRepository jpa, SigmaYamlParser parser) {
    this.jpa = jpa;
    this.parser = parser;
  }

  @Override
  public SigmaRule save(SigmaRule sigma, TenantId tenantId, UUID alertRuleId) {
    var entity = SigmaRuleEntity.from(sigma, tenantId.value(), alertRuleId);
    jpa.save(entity);
    return sigma;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SigmaRule> findBySigmaIdAndTenant(String sigmaId, TenantId tenantId) {
    return jpa.findBySigmaIdAndTenantId(sigmaId, tenantId.value()).map(this::reparse);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SigmaRule> findByTenant(TenantId tenantId) {
    return jpa.findByTenantId(tenantId.value()).stream().map(this::reparse).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findAlertRuleId(String sigmaId, TenantId tenantId) {
    return jpa.findBySigmaIdAndTenantId(sigmaId, tenantId.value())
        .map(SigmaRuleEntity::getAlertRuleId);
  }

  @Override
  public void deleteBySigmaIdAndTenant(String sigmaId, TenantId tenantId) {
    jpa.deleteBySigmaIdAndTenantId(sigmaId, tenantId.value());
  }

  private SigmaRule reparse(SigmaRuleEntity e) {
    return e.toDomain(parser::parseSingle);
  }
}
