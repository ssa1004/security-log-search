package com.example.security.adapter.out.jpa

import com.example.security.adapter.out.jpa.entity.SigmaRuleEntity
import com.example.security.adapter.out.jpa.repository.SigmaRuleJpaRepository
import com.example.security.application.port.out.SigmaRuleRepository
import com.example.security.application.sigma.SigmaYamlParser
import com.example.security.domain.common.TenantId
import com.example.security.domain.sigma.SigmaRule
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class JpaSigmaRuleRepository(
    private val jpa: SigmaRuleJpaRepository,
    private val parser: SigmaYamlParser,
) : SigmaRuleRepository {

    override fun save(sigma: SigmaRule, tenantId: TenantId, alertRuleId: UUID): SigmaRule {
        val entity = SigmaRuleEntity.from(sigma, tenantId.value, alertRuleId)
        jpa.save(entity)
        return sigma
    }

    @Transactional(readOnly = true)
    override fun findBySigmaIdAndTenant(sigmaId: String, tenantId: TenantId): Optional<SigmaRule> =
        jpa.findBySigmaIdAndTenantId(sigmaId, tenantId.value).map { reparse(it) }

    @Transactional(readOnly = true)
    override fun findByTenant(tenantId: TenantId): List<SigmaRule> =
        jpa.findByTenantId(tenantId.value).map { reparse(it) }

    @Transactional(readOnly = true)
    override fun findAlertRuleId(sigmaId: String, tenantId: TenantId): Optional<UUID> =
        jpa.findBySigmaIdAndTenantId(sigmaId, tenantId.value).map { it.alertRuleId }

    override fun deleteBySigmaIdAndTenant(sigmaId: String, tenantId: TenantId) {
        jpa.deleteBySigmaIdAndTenantId(sigmaId, tenantId.value)
    }

    private fun reparse(e: SigmaRuleEntity): SigmaRule = e.toDomain { parser.parseSingle(it) }
}
