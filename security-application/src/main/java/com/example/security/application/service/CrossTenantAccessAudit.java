package com.example.security.application.service;

import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 플랫폼 관리자 (PLATFORM_ADMIN) 가 본인 소속 외 tenant 의 데이터에 접근할 때 audit 기록.
 *
 * <p>일반 운영자의 tenant 불일치는 {@code TenantMismatchException} 으로 거부되지만, PLATFORM_ADMIN
 * 은 모든 tenant 접근이 허용된다 (ADR-0007 Layer 4 의 admin 우회). 이 우회 자체가 ISMS-P 2.6
 * (접근 통제) 에서 추적 대상 — "누가 본인 tenant 가 아닌 곳을 들여다봤는가" 를 audit_entries 에
 * 남겨야 한다.
 *
 * <p>각 use case 가 권한 검증 직후 본 helper 를 호출한다. 같은 tenant 접근 (우회 아님) 이면
 * 아무 것도 하지 않는다.
 */
final class CrossTenantAccessAudit {

  private CrossTenantAccessAudit() {}

  /**
   * operator 가 본인 외 tenant 에 접근하는 경우에만 {@code CROSS_TENANT_ACCESS} audit 을 남긴다.
   *
   * @param targetType 접근 대상 자원 종류 (예: "search", "stats", "alert", "index")
   * @param targetId 접근 대상 식별자 (구체 id 가 없으면 query 요약 등)
   */
  static void recordIfCrossTenant(
      AuditLogPort audit,
      Clock clock,
      OperatorContext operator,
      TenantId target,
      String targetType,
      String targetId) {
    if (operator.tenantId().equals(target)) {
      return;
    }
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            target,
            clock.instant(),
            operator.subject(),
            operator.roles().stream().map(Enum::name).collect(Collectors.joining(",")),
            AuditAction.CROSS_TENANT_ACCESS,
            targetType,
            targetId,
            operator.sourceIp(),
            Map.of(
                "operator_tenant", operator.tenantId().value(),
                "accessed_tenant", target.value())));
  }
}
