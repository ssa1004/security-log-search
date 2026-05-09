package com.example.security.adapter.out.jpa;

import com.example.security.adapter.out.jpa.entity.TenantEntity;
import com.example.security.adapter.out.jpa.repository.TenantJpaRepository;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.tenant.Tenant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JpaTenantRepository implements TenantRepository {

  private final TenantJpaRepository jpa;

  public JpaTenantRepository(TenantJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Tenant save(Tenant tenant) {
    return jpa.save(TenantEntity.from(tenant)).toDomain();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Tenant> findById(TenantId tenantId) {
    return jpa.findById(tenantId.value()).map(TenantEntity::toDomain);
  }
}
