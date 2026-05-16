package com.example.security.application.port.out

import com.example.security.domain.common.TenantId
import com.example.security.domain.sigma.SigmaRule
import java.util.Optional
import java.util.UUID

/**
 * import 한 Sigma 룰의 영속 — Postgres `sigma_rules` 테이블.
 *
 * 원본 YAML 도 함께 보관하여 재변환 / 감사에 사용한다.
 */
interface SigmaRuleRepository {

    /** import 된 Sigma 룰을 tenant + 변환 결과 alert_rule_id 와 함께 저장. */
    fun save(sigma: SigmaRule, tenantId: TenantId, alertRuleId: UUID): SigmaRule

    fun findBySigmaIdAndTenant(sigmaId: String, tenantId: TenantId): Optional<SigmaRule>

    fun findByTenant(tenantId: TenantId): List<SigmaRule>

    /** 지정 sigma id + tenant 인 record 의 alert_rule_id 를 반환. 없으면 empty. */
    fun findAlertRuleId(sigmaId: String, tenantId: TenantId): Optional<UUID>

    fun deleteBySigmaIdAndTenant(sigmaId: String, tenantId: TenantId)
}
