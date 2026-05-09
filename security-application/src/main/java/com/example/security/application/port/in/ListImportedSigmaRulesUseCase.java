package com.example.security.application.port.in;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.sigma.SigmaRule;
import java.util.List;

/** use case 10 — import 한 Sigma 룰 목록 조회. */
public interface ListImportedSigmaRulesUseCase {

  List<SigmaRule> listByTenant(TenantId tenantId, OperatorContext operator);
}
