package com.example.security.application.port.in;

import com.example.security.domain.common.TenantId;
import com.example.security.domain.tenant.Tenant;
import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;
import java.time.Duration;

/**
 * use case 9 — 신규 tenant onboarding.
 *
 * <p>등록 시 다음을 자동 wiring:
 *
 * <ul>
 *   <li>OpenSearch — events-{tenant}-write / -read alias + 초기 인덱스 + ILM 정책
 *   <li>ClickHouse — Row Policy (WHERE tenant_id = currentSetting('tenant_id'))
 *   <li>Postgres — tenants 테이블 INSERT
 *   <li>audit_entries — TENANT_ONBOARDED 기록
 * </ul>
 */
public interface OnboardTenantUseCase {

  Tenant onboard(OnboardCommand command, OperatorContext operator);

  void deactivate(TenantId tenantId, OperatorContext operator);

  record OnboardCommand(
      TenantId tenantId,
      String displayName,
      Duration retention,
      Duration hotRetention,
      PiiMaskingPolicy piiPolicy) {}
}
