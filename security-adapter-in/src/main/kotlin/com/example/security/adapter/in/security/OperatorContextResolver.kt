package com.example.security.adapter.`in`.security

import com.example.security.application.port.`in`.OperatorContext
import com.example.security.application.port.`in`.OperatorContext.Role
import com.example.security.domain.common.TenantId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 현재 요청의 SecurityContext 에서 OperatorContext 를 만든다.
 *
 * JWT claim 매핑:
 *
 *  - `sub` → subject
 *  - `tenant_id` → tenantId (필수)
 *  - `roles` (배열) → Role enum (OPERATOR / ADMIN / PLATFORM_ADMIN)
 *
 * local / dev profile 에서는 SecurityFilter 가 anonymous 일 수 있으므로 SecurityContext 가
 * 비어있으면 default operator (tenantId=acme) 로 fallback (테스트 / 개발 편의).
 */
@Component
class OperatorContextResolver {

    fun currentOperator(): OperatorContext {
        val auth = SecurityContextHolder.getContext().authentication
        val sourceIp = currentSourceIp()
        if (auth == null || !auth.isAuthenticated || auth.principal !is Jwt) {
            // dev fallback — 모든 권한 부여 + acme tenant.
            return OperatorContext(
                "anonymous",
                TenantId.of("acme"),
                sourceIp,
                setOf(Role.OPERATOR, Role.ADMIN, Role.PLATFORM_ADMIN),
            )
        }
        val jwt = auth.principal as Jwt
        val subject = jwt.subject
        val tenantClaim = jwt.getClaimAsString("tenant_id")
        check(!tenantClaim.isNullOrBlank()) { "JWT 에 tenant_id claim 필수" }
        val tenantId = TenantId.of(tenantClaim)
        val roleStrings = jwt.getClaimAsStringList("roles")
        return OperatorContext(subject, tenantId, sourceIp, mapRoles(roleStrings))
    }

    /** JWT 가 없는 simple test 에서 명시적으로 만들 때. */
    fun fromAuthentication(auth: Authentication?, req: HttpServletRequest?): OperatorContext {
        if (auth == null || !auth.isAuthenticated || auth.principal !is Jwt) {
            return OperatorContext(
                "anonymous",
                TenantId.of("acme"),
                req?.remoteAddr,
                setOf(Role.OPERATOR, Role.ADMIN, Role.PLATFORM_ADMIN),
            )
        }
        val jwt = auth.principal as Jwt
        val tenantClaim = jwt.getClaimAsString("tenant_id")
        check(!tenantClaim.isNullOrBlank()) { "JWT 에 tenant_id claim 필수" }
        return OperatorContext(
            jwt.subject,
            TenantId.of(tenantClaim),
            req?.remoteAddr,
            mapRoles(jwt.getClaimAsStringList("roles")),
        )
    }

    companion object {
        private fun mapRoles(roles: List<String>?): Set<Role> {
            if (roles == null) return setOf(Role.OPERATOR)
            val out = HashSet<Role>()
            for (r in roles) {
                try {
                    out.add(Role.valueOf(r.uppercase()))
                } catch (_: IllegalArgumentException) {
                    // 모르는 role 무시
                }
            }
            if (out.isEmpty()) out.add(Role.OPERATOR)
            return out.toSet()
        }

        private fun currentSourceIp(): String? {
            val attr = RequestContextHolder.getRequestAttributes()
            if (attr is ServletRequestAttributes) {
                val req = attr.request
                val xff = req.getHeader("X-Forwarded-For")
                if (!xff.isNullOrBlank()) {
                    return xff.split(",")[0].trim()
                }
                return req.remoteAddr
            }
            return null
        }
    }
}
