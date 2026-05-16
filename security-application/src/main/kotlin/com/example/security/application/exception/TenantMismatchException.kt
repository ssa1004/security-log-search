package com.example.security.application.exception

import com.example.security.domain.common.TenantId

/**
 * 운영자의 tenantId 와 요청 query 의 tenantId 가 다른 경우.
 *
 * 일반 운영자가 다른 tenant 의 데이터에 접근하려는 시도 — 즉시 거부 + audit 기록.
 */
class TenantMismatchException(operator: TenantId, requested: TenantId) :
    RuntimeException("운영자 tenant (${operator.value}) 와 요청 tenant (${requested.value}) 불일치")
