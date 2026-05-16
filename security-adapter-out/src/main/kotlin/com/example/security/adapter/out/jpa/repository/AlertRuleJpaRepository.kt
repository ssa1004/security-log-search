package com.example.security.adapter.out.jpa.repository

import com.example.security.adapter.out.jpa.entity.AlertRuleEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AlertRuleJpaRepository : JpaRepository<AlertRuleEntity, UUID> {

    fun findByTenantIdAndEnabledTrue(tenantId: String): List<AlertRuleEntity>

    fun findByEnabledTrue(): List<AlertRuleEntity>
}
