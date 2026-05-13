package com.example.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.security.application.exception.InsufficientPrivilegeException;
import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.ManageOpenSearchIndexUseCase.RolloverResult;
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
 * 인덱스 admin 권한 검증 — OWASP API5 (Broken Function Level Authorization) 회귀 방지.
 *
 * <p>OPERATOR role 만 가진 운영자가 admin endpoint (인덱스 create / rollover / ILM) 를 호출하면
 * {@link InsufficientPrivilegeException} 으로 거부되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class ManageOpenSearchIndexServiceTest {

  @Mock IndexAdminPort indexAdmin;
  @Mock TenantRepository tenants;
  @Mock AuditLogPort audit;

  private ManageOpenSearchIndexService service;
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC);
  private final TenantId tenantId = TenantId.of("acme");
  private final Tenant tenant =
      new Tenant(
          tenantId,
          "Acme",
          Duration.ofDays(365),
          Duration.ofDays(7),
          PiiMaskingPolicy.IP_ONLY,
          Instant.parse("2026-01-01T00:00:00Z"),
          true);

  @BeforeEach
  void setup() {
    service = new ManageOpenSearchIndexService(indexAdmin, tenants, audit, clock);
  }

  @Test
  void OPERATOR_는_인덱스_create_불가() {
    var operator = new OperatorContext("alice", tenantId, "127.0.0.1", Set.of(Role.OPERATOR));

    assertThatThrownBy(() -> service.createInitialIndex(tenantId, operator))
        .isInstanceOf(InsufficientPrivilegeException.class);
    verify(indexAdmin, never()).provisionForTenant(any());
  }

  @Test
  void OPERATOR_는_rollover_불가() {
    var operator = new OperatorContext("alice", tenantId, "127.0.0.1", Set.of(Role.OPERATOR));

    assertThatThrownBy(() -> service.triggerRollover(tenantId, operator))
        .isInstanceOf(InsufficientPrivilegeException.class);
    verify(indexAdmin, never()).triggerRollover(any());
  }

  @Test
  void OPERATOR_는_ilm_적용_불가() {
    var operator = new OperatorContext("alice", tenantId, "127.0.0.1", Set.of(Role.OPERATOR));

    assertThatThrownBy(() -> service.applyIlmPolicy(tenantId, operator))
        .isInstanceOf(InsufficientPrivilegeException.class);
    verify(indexAdmin, never()).applyIlmPolicy(any());
  }

  @Test
  void ADMIN_은_본_tenant_의_rollover_허용() {
    var operator = new OperatorContext("admin1", tenantId, "127.0.0.1", Set.of(Role.ADMIN));
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(indexAdmin.triggerRollover(any())).thenReturn(new RolloverResult(true, "events-acme-000001", "events-acme-000002"));

    var result = service.triggerRollover(tenantId, operator);

    verify(indexAdmin).triggerRollover(tenant);
    verify(audit).append(any());
    assertThat(result.rolledOver()).isTrue();
  }

  @Test
  void ADMIN_은_다른_tenant_의_rollover_불가() {
    var operator = new OperatorContext("admin1", TenantId.of("other"), "127.0.0.1", Set.of(Role.ADMIN));

    assertThatThrownBy(() -> service.triggerRollover(tenantId, operator))
        .isInstanceOf(TenantMismatchException.class);
    verify(indexAdmin, never()).triggerRollover(any());
  }

  @Test
  void PLATFORM_ADMIN_은_다른_tenant_의_rollover_허용() {
    var operator = new OperatorContext("pa", TenantId.of("other"), "127.0.0.1", Set.of(Role.PLATFORM_ADMIN));
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(indexAdmin.triggerRollover(any())).thenReturn(new RolloverResult(false, null, null));

    service.triggerRollover(tenantId, operator);

    verify(indexAdmin).triggerRollover(tenant);
  }
}
