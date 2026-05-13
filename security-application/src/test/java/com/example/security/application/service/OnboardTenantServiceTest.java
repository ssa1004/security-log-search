package com.example.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.security.application.exception.InsufficientPrivilegeException;
import com.example.security.application.port.in.OnboardTenantUseCase.OnboardCommand;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.OperatorContext.Role;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.IndexAdminPort;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.tenant.Tenant;
import com.example.security.domain.tenant.Tenant.PiiMaskingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 테넌트 onboarding 권한 검증 — OWASP API5 (Broken Function Level Authorization) 회귀 방지.
 *
 * <p>tenant 라이프사이클 (onboard / deactivate) 은 플랫폼 운영자 (PLATFORM_ADMIN) 만 호출 가능
 * — 다른 tenant 의 ADMIN 이 새 tenant 를 만들거나 비활성화하지 못하도록 차단.
 */
@ExtendWith(MockitoExtension.class)
class OnboardTenantServiceTest {

  @Mock TenantRepository tenants;
  @Mock IndexAdminPort indexAdmin;
  @Mock AuditLogPort audit;

  private OnboardTenantService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC);
  private final TenantId tenantId = TenantId.of("acme");
  private final OnboardCommand cmd =
      new OnboardCommand(
          tenantId,
          "Acme",
          Duration.ofDays(365),
          Duration.ofDays(7),
          PiiMaskingPolicy.IP_ONLY);

  @BeforeEach
  void setup() {
    service = new OnboardTenantService(tenants, indexAdmin, audit, clock);
  }

  @Test
  void OPERATOR_는_onboard_불가() {
    var operator = new OperatorContext("alice", tenantId, "127.0.0.1", Set.of(Role.OPERATOR));

    assertThatThrownBy(() -> service.onboard(cmd, operator))
        .isInstanceOf(InsufficientPrivilegeException.class);
    verify(tenants, never()).save(any());
    verify(indexAdmin, never()).provisionForTenant(any());
  }

  @Test
  void ADMIN_은_onboard_불가() {
    var operator = new OperatorContext("admin1", tenantId, "127.0.0.1", Set.of(Role.ADMIN));

    assertThatThrownBy(() -> service.onboard(cmd, operator))
        .isInstanceOf(InsufficientPrivilegeException.class);
    verify(tenants, never()).save(any());
  }

  @Test
  void PLATFORM_ADMIN_은_onboard_허용() {
    var operator = new OperatorContext("pa", tenantId, "127.0.0.1", Set.of(Role.PLATFORM_ADMIN));
    when(tenants.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.onboard(cmd, operator);

    assertThat(result.tenantId()).isEqualTo(tenantId);
    verify(indexAdmin).provisionForTenant(any());
    verify(indexAdmin).applyIlmPolicy(any());
    verify(indexAdmin).provisionClickHouseRowPolicy(any());
    verify(audit).append(any());
  }

  @Test
  void OPERATOR_는_deactivate_불가() {
    var operator = new OperatorContext("alice", tenantId, "127.0.0.1", Set.of(Role.OPERATOR));

    assertThatThrownBy(() -> service.deactivate(tenantId, operator))
        .isInstanceOf(InsufficientPrivilegeException.class);
    verify(tenants, never()).save(any());
  }

  @Test
  void PLATFORM_ADMIN_은_deactivate_허용() {
    var operator = new OperatorContext("pa", tenantId, "127.0.0.1", Set.of(Role.PLATFORM_ADMIN));
    var existing =
        new Tenant(
            tenantId,
            "Acme",
            Duration.ofDays(365),
            Duration.ofDays(7),
            PiiMaskingPolicy.IP_ONLY,
            Instant.parse("2026-01-01T00:00:00Z"),
            true);
    when(tenants.findById(tenantId)).thenReturn(Optional.of(existing));
    when(tenants.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.deactivate(tenantId, operator);

    verify(audit).append(any());
  }
}
