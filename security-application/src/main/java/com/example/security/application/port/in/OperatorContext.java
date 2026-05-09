package com.example.security.application.port.in;

import com.example.security.domain.common.TenantId;
import java.util.Objects;
import java.util.Set;

/**
 * 운영자 컨텍스트 — JWT 에서 추출한 tenant / role / sub.
 *
 * <p>모든 use case 에 명시적으로 전달. application layer 가 query 의 tenantId 가
 * operator.tenantId 와 일치하는지 (또는 admin role 인지) 검증.
 */
public record OperatorContext(String subject, TenantId tenantId, String sourceIp, Set<Role> roles) {

  public OperatorContext {
    Objects.requireNonNull(subject);
    Objects.requireNonNull(tenantId);
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public boolean isAdmin() {
    return roles.contains(Role.ADMIN);
  }

  public boolean canQueryOtherTenant() {
    return roles.contains(Role.PLATFORM_ADMIN);
  }

  public enum Role {
    /** 일반 운영자 — 자기 tenant 검색 / 알람 처리. */
    OPERATOR,
    /** tenant 관리자 — 룰 CRUD / 인덱스 관리. */
    ADMIN,
    /** 플랫폼 관리자 — 모든 tenant 접근 가능. */
    PLATFORM_ADMIN
  }
}
