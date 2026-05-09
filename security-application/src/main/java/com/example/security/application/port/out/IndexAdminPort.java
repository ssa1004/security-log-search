package com.example.security.application.port.out;

import com.example.security.application.port.in.ManageOpenSearchIndexUseCase.RolloverResult;
import com.example.security.domain.tenant.Tenant;

/** OpenSearch 인덱스 관리 어댑터. */
public interface IndexAdminPort {

  /** tenant onboarding 시 호출. write / read alias + 첫 인덱스 + ILM policy 적용. */
  void provisionForTenant(Tenant tenant);

  /** rollover 시도. write alias 의 현재 인덱스가 size/age 임계 도달 시 새 인덱스로 swap. */
  RolloverResult triggerRollover(Tenant tenant);

  /** ILM policy 적용 / 갱신 — hot / warm / cold / delete 단계. */
  void applyIlmPolicy(Tenant tenant);

  /** ClickHouse Row Policy 도 같이 wiring (tenant onboarding 시). */
  void provisionClickHouseRowPolicy(Tenant tenant);
}
