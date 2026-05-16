package com.example.security.application.port.`in`

import com.example.security.domain.common.TenantId
import com.example.security.domain.sigma.SigmaRule

/** use case 10 — import 한 Sigma 룰 목록 조회. */
interface ListImportedSigmaRulesUseCase {

    fun listByTenant(tenantId: TenantId, operator: OperatorContext): List<SigmaRule>
}
