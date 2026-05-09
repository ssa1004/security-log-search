package com.example.security.application.port.in;

import com.example.security.domain.common.TenantId;

/**
 * use case 7 — admin endpoint. OpenSearch 인덱스 / alias / ILM 정책 관리.
 *
 * <p>주로 운영자가 호출. ALIAS_SWAP / INDEX_ROLLOVER 같은 동작은 모두 audit_entries 에 기록.
 */
public interface ManageOpenSearchIndexUseCase {

  /** 새 인덱스 생성 + write alias 가리키기 + read alias 추가. tenant onboarding 시 호출. */
  void createInitialIndex(TenantId tenantId, OperatorContext operator);

  /** rollover trigger — write alias 의 현재 인덱스가 임계 (size / age) 도달했는지 확인 후 swap. */
  RolloverResult triggerRollover(TenantId tenantId, OperatorContext operator);

  /** ILM 정책 (hot/warm/cold/delete) 적용. */
  void applyIlmPolicy(TenantId tenantId, OperatorContext operator);

  record RolloverResult(boolean rolledOver, String oldIndex, String newIndex) {}
}
