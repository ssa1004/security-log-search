package com.example.security.application.port.`in`

import com.example.security.domain.common.TenantId

/**
 * 운영자 컨텍스트 — JWT 에서 추출한 tenant / role / sub.
 *
 * 모든 use case 에 명시적으로 전달. application layer 가 query 의 tenantId 가
 * operator.tenantId 와 일치하는지 (또는 admin role 인지) 검증.
 */
@JvmRecord
data class OperatorContext(
    val subject: String,
    val tenantId: TenantId,
    val sourceIp: String?,
    val roles: Set<Role>,
) {

    fun isAdmin(): Boolean = roles.contains(Role.ADMIN)

    fun canQueryOtherTenant(): Boolean = roles.contains(Role.PLATFORM_ADMIN)

    enum class Role {
        /** 일반 운영자 — 자기 tenant 검색 / 알람 처리. */
        OPERATOR,

        /** tenant 관리자 — 룰 CRUD / 인덱스 관리. */
        ADMIN,

        /** 플랫폼 관리자 — 모든 tenant 접근 가능. */
        PLATFORM_ADMIN,
    }
}
