package com.example.security.adapter.out.jpa

import com.example.security.adapter.out.jpa.entity.AlertRuleEntity
import com.example.security.adapter.out.jpa.repository.AlertRuleJpaRepository
import com.example.security.application.port.out.AlertRuleRepository
import com.example.security.domain.common.TenantId
import com.example.security.domain.rule.AlertRule
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class JpaAlertRuleRepository(
    private val jpa: AlertRuleJpaRepository,
) : AlertRuleRepository {

    override fun save(rule: AlertRule): AlertRule =
        jpa.save(AlertRuleEntity.from(rule)).toDomain()

    @Transactional(readOnly = true)
    override fun findById(ruleId: UUID): Optional<AlertRule> =
        jpa.findById(ruleId).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findEnabledByTenant(tenantId: TenantId): List<AlertRule> =
        jpa.findByTenantIdAndEnabledTrue(tenantId.value).map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findAllEnabled(): List<AlertRule> =
        jpa.findByEnabledTrue().map { it.toDomain() }

    override fun deleteById(ruleId: UUID) {
        jpa.deleteById(ruleId)
    }
}
