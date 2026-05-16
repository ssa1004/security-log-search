package com.example.security.application.port.out

import com.example.security.domain.common.TenantId
import com.example.security.domain.tenant.Tenant
import java.util.Optional

/** 테넌트 영속 — Postgres tenants 테이블. */
interface TenantRepository {

    fun save(tenant: Tenant): Tenant

    fun findById(tenantId: TenantId): Optional<Tenant>
}
