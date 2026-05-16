package com.example.security.adapter.out.jpa.repository

import com.example.security.adapter.out.jpa.entity.SigmaRuleEntity
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface SigmaRuleJpaRepository : JpaRepository<SigmaRuleEntity, SigmaRuleEntity.PK> {

    fun findBySigmaIdAndTenantId(sigmaId: String, tenantId: String): Optional<SigmaRuleEntity>

    fun findByTenantId(tenantId: String): List<SigmaRuleEntity>

    fun deleteBySigmaIdAndTenantId(sigmaId: String, tenantId: String)
}
