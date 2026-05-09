package com.example.security.adapter.in.security;

import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.OperatorContext.Role;
import com.example.security.domain.common.TenantId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 현재 요청의 SecurityContext 에서 OperatorContext 를 만든다.
 *
 * <p>JWT claim 매핑:
 *
 * <ul>
 *   <li>{@code sub} → subject
 *   <li>{@code tenant_id} → tenantId (필수)
 *   <li>{@code roles} (배열) → Role enum (OPERATOR / ADMIN / PLATFORM_ADMIN)
 * </ul>
 *
 * <p>local / dev profile 에서는 SecurityFilter 가 anonymous 일 수 있으므로 SecurityContext 가
 * 비어있으면 default operator (tenantId=acme) 로 fallback (테스트 / 개발 편의).
 */
@Component
public class OperatorContextResolver {

  public OperatorContext currentOperator() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    var sourceIp = currentSourceIp();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
      // dev fallback — 모든 권한 부여 + acme tenant.
      return new OperatorContext(
          "anonymous",
          TenantId.of("acme"),
          sourceIp,
          Set.of(Role.OPERATOR, Role.ADMIN, Role.PLATFORM_ADMIN));
    }
    var subject = jwt.getSubject();
    var tenantClaim = jwt.getClaimAsString("tenant_id");
    if (tenantClaim == null || tenantClaim.isBlank()) {
      throw new IllegalStateException("JWT 에 tenant_id claim 필수");
    }
    var tenantId = TenantId.of(tenantClaim);
    var roleStrings = jwt.getClaimAsStringList("roles");
    return new OperatorContext(subject, tenantId, sourceIp, mapRoles(roleStrings));
  }

  /** JWT 가 없는 simple test 에서 명시적으로 만들 때. */
  public OperatorContext fromAuthentication(Authentication auth, HttpServletRequest req) {
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return new OperatorContext(
          "anonymous",
          TenantId.of("acme"),
          req != null ? req.getRemoteAddr() : null,
          Set.of(Role.OPERATOR, Role.ADMIN, Role.PLATFORM_ADMIN));
    }
    var tenantClaim = jwt.getClaimAsString("tenant_id");
    if (tenantClaim == null || tenantClaim.isBlank()) {
      throw new IllegalStateException("JWT 에 tenant_id claim 필수");
    }
    return new OperatorContext(
        jwt.getSubject(),
        TenantId.of(tenantClaim),
        req != null ? req.getRemoteAddr() : null,
        mapRoles(jwt.getClaimAsStringList("roles")));
  }

  private static Set<Role> mapRoles(List<String> roles) {
    if (roles == null) return Set.of(Role.OPERATOR);
    Set<Role> out = new HashSet<>();
    for (var r : roles) {
      try {
        out.add(Role.valueOf(r.toUpperCase()));
      } catch (IllegalArgumentException ignore) {
        // 모르는 role 무시
      }
    }
    if (out.isEmpty()) out.add(Role.OPERATOR);
    return Set.copyOf(out);
  }

  private static String currentSourceIp() {
    var attr = RequestContextHolder.getRequestAttributes();
    if (attr instanceof ServletRequestAttributes sra) {
      var req = sra.getRequest();
      var xff = req.getHeader("X-Forwarded-For");
      if (xff != null && !xff.isBlank()) {
        return xff.split(",")[0].trim();
      }
      return req.getRemoteAddr();
    }
    return null;
  }
}
