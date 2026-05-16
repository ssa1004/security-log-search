package com.example.security.application.port.`in`

import com.example.security.domain.common.TenantId

/**
 * use case 7 — admin endpoint. OpenSearch 인덱스 / alias / ILM 정책 관리.
 *
 * 주로 운영자가 호출. ALIAS_SWAP / INDEX_ROLLOVER 같은 동작은 모두 audit_entries 에 기록.
 */
interface ManageOpenSearchIndexUseCase {

    /** 새 인덱스 생성 + write alias 가리키기 + read alias 추가. tenant onboarding 시 호출. */
    fun createInitialIndex(tenantId: TenantId, operator: OperatorContext)

    /** rollover trigger — write alias 의 현재 인덱스가 임계 (size / age) 도달했는지 확인 후 swap. */
    fun triggerRollover(tenantId: TenantId, operator: OperatorContext): RolloverResult

    /** ILM 정책 (hot/warm/cold/delete) 적용. */
    fun applyIlmPolicy(tenantId: TenantId, operator: OperatorContext)

    @JvmRecord
    data class RolloverResult(val rolledOver: Boolean, val oldIndex: String?, val newIndex: String?)
}
