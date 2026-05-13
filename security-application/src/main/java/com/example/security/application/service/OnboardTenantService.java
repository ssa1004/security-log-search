package com.example.security.application.service;

import com.example.security.application.exception.InsufficientPrivilegeException;
import com.example.security.application.exception.TenantNotFoundException;
import com.example.security.application.port.in.OnboardTenantUseCase;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.IndexAdminPort;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.tenant.Tenant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** use case 9 — 신규 tenant onboarding. */
@Service
public class OnboardTenantService implements OnboardTenantUseCase {

  private final TenantRepository tenants;
  private final IndexAdminPort indexAdmin;
  private final AuditLogPort audit;
  private final Clock clock;

  public OnboardTenantService(
      TenantRepository tenants,
      IndexAdminPort indexAdmin,
      AuditLogPort audit,
      Clock clock) {
    this.tenants = tenants;
    this.indexAdmin = indexAdmin;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Tenant onboard(OnboardCommand cmd, OperatorContext operator) {
    enforcePlatformAdmin(operator);
    var tenant =
        new Tenant(
            cmd.tenantId(),
            cmd.displayName(),
            cmd.retention(),
            cmd.hotRetention(),
            cmd.piiPolicy(),
            clock.instant(),
            true);
    var saved = tenants.save(tenant);
    indexAdmin.provisionForTenant(saved);
    indexAdmin.applyIlmPolicy(saved);
    indexAdmin.provisionClickHouseRowPolicy(saved);

    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            saved.tenantId(),
            clock.instant(),
            operator.subject(),
            operator.roles().stream().map(Enum::name).collect(Collectors.joining(",")),
            AuditAction.TENANT_ONBOARDED,
            "tenant",
            saved.tenantId().value(),
            operator.sourceIp(),
            Map.of(
                "displayName", saved.displayName(),
                "retention", saved.retention().toString(),
                "hotRetention", saved.hotRetention().toString(),
                "piiPolicy", saved.piiPolicy().name())));

    return saved;
  }

  @Override
  @Transactional
  public void deactivate(TenantId tenantId, OperatorContext operator) {
    enforcePlatformAdmin(operator);
    var existing = tenants.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    var deactivated =
        new Tenant(
            existing.tenantId(),
            existing.displayName(),
            existing.retention(),
            existing.hotRetention(),
            existing.piiPolicy(),
            existing.onboardedAt(),
            false);
    tenants.save(deactivated);
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            tenantId,
            clock.instant(),
            operator.subject(),
            operator.roles().stream().map(Enum::name).collect(Collectors.joining(",")),
            AuditAction.TENANT_DEACTIVATED,
            "tenant",
            tenantId.value(),
            operator.sourceIp(),
            Map.of("active", "false")));
  }

  /**
   * tenant 라이프사이클 (onboard / deactivate) 은 플랫폼 운영자만 — 한 tenant 의 ADMIN 이
   * 다른 tenant 를 생성 / 비활성화하는 것을 차단.
   */
  private static void enforcePlatformAdmin(OperatorContext operator) {
    if (!operator.canQueryOtherTenant()) {
      throw new InsufficientPrivilegeException("PLATFORM_ADMIN");
    }
  }
}
