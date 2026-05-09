package com.example.security.application.port.out;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.sigma.SigmaRule;
import java.util.List;
import java.util.Optional;

/**
 * import 한 Sigma 룰의 영속 — Postgres {@code sigma_rules} 테이블.
 *
 * <p>원본 YAML 도 함께 보관하여 재변환 / 감사에 사용한다.
 */
public interface SigmaRuleRepository {

  /** import 된 Sigma 룰을 tenant + 변환 결과 alert_rule_id 와 함께 저장. */
  SigmaRule save(SigmaRule sigma, TenantId tenantId, java.util.UUID alertRuleId);

  Optional<SigmaRule> findBySigmaIdAndTenant(String sigmaId, TenantId tenantId);

  List<SigmaRule> findByTenant(TenantId tenantId);

  /** 지정 sigma id + tenant 인 record 의 alert_rule_id 를 반환. 없으면 empty. */
  Optional<java.util.UUID> findAlertRuleId(String sigmaId, TenantId tenantId);

  void deleteBySigmaIdAndTenant(String sigmaId, TenantId tenantId);
}
