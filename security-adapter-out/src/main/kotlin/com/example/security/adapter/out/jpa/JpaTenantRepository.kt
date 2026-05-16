package com.example.security.adapter.out.jpa

import com.example.security.adapter.out.jpa.entity.TenantEntity
import com.example.security.adapter.out.jpa.repository.TenantJpaRepository
import com.example.security.application.port.out.TenantRepository
import com.example.security.domain.common.TenantId
import com.example.security.domain.tenant.Tenant
import java.util.Optional
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class JpaTenantRepository(
    private val jpa: TenantJpaRepository,
) : TenantRepository {

    override fun save(tenant: Tenant): Tenant = jpa.save(TenantEntity.from(tenant)).toDomain()

    @Transactional(readOnly = true)
    override fun findById(tenantId: TenantId): Optional<Tenant> =
        jpa.findById(tenantId.value).map { it.toDomain() }
}
