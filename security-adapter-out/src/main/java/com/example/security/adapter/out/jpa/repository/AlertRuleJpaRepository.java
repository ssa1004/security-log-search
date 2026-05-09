package com.example.security.adapter.out.jpa.repository;

import com.example.security.adapter.out.jpa.entity.AlertRuleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleJpaRepository extends JpaRepository<AlertRuleEntity, UUID> {

  List<AlertRuleEntity> findByTenantIdAndEnabledTrue(String tenantId);

  List<AlertRuleEntity> findByEnabledTrue();
}
