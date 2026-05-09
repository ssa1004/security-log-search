package com.example.security.adapter.out.jpa.repository;

import com.example.security.adapter.out.jpa.entity.SigmaRuleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigmaRuleJpaRepository
    extends JpaRepository<SigmaRuleEntity, SigmaRuleEntity.PK> {

  Optional<SigmaRuleEntity> findBySigmaIdAndTenantId(String sigmaId, String tenantId);

  List<SigmaRuleEntity> findByTenantId(String tenantId);

  void deleteBySigmaIdAndTenantId(String sigmaId, String tenantId);
}
