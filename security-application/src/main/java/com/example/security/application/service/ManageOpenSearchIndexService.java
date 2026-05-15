package com.example.security.application.service;

import com.example.security.application.exception.InsufficientPrivilegeException;
import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.exception.TenantNotFoundException;
import com.example.security.application.port.in.ManageOpenSearchIndexUseCase;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.application.port.out.IndexAdminPort;
import com.example.security.application.port.out.TenantRepository;
import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** use case 7 — admin endpoint. */
@Service
public class ManageOpenSearchIndexService implements ManageOpenSearchIndexUseCase {

  private final IndexAdminPort indexAdmin;
  private final TenantRepository tenants;
  private final AuditLogPort audit;
  private final Clock clock;

  public ManageOpenSearchIndexService(
      IndexAdminPort indexAdmin,
      TenantRepository tenants,
      AuditLogPort audit,
      Clock clock) {
    this.indexAdmin = indexAdmin;
    this.tenants = tenants;
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public void createInitialIndex(TenantId tenantId, OperatorContext operator) {
    enforceAdmin(operator, tenantId);
    var tenant = tenants.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    indexAdmin.provisionForTenant(tenant);
    appendAudit(tenantId, operator, AuditAction.INDEX_CREATED, Map.of("tenant", tenantId.value()));
  }

  @Override
  public RolloverResult triggerRollover(TenantId tenantId, OperatorContext operator) {
    enforceAdmin(operator, tenantId);
    var tenant = tenants.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    var result = indexAdmin.triggerRollover(tenant);
    if (result.rolledOver()) {
      appendAudit(
          tenantId,
          operator,
          AuditAction.INDEX_ROLLOVER,
          Map.of("from", result.oldIndex(), "to", result.newIndex()));
    }
    return result;
  }

  @Override
  public void applyIlmPolicy(TenantId tenantId, OperatorContext operator) {
    enforceAdmin(operator, tenantId);
    var tenant = tenants.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    indexAdmin.applyIlmPolicy(tenant);
    appendAudit(
        tenantId,
        operator,
        AuditAction.ILM_POLICY_APPLIED,
        Map.of("policy", tenant.ilmPolicyName()));
  }

  /**
   * 인덱스 admin 동작 (생성 / rollover / ILM 적용) 은 tenant ADMIN role 이상 + 본 tenant 또는
   * PLATFORM_ADMIN 이 다른 tenant 를 관리할 때만 허용.
   *
   * <p>ISMS-P 2.6 (접근 통제) — function-level authorization 분리. PLATFORM_ADMIN 이 본인 외
   * tenant 인덱스를 관리하면 cross-tenant access 로 audit 에 남긴다.
   */
  private void enforceAdmin(OperatorContext operator, TenantId tenant) {
    if (operator.canQueryOtherTenant()) {
      CrossTenantAccessAudit.recordIfCrossTenant(
          audit, clock, operator, tenant, "index", tenant.value());
      return;
    }
    if (!operator.isAdmin()) {
      throw new InsufficientPrivilegeException("ADMIN");
    }
    if (!operator.tenantId().equals(tenant)) {
      throw new TenantMismatchException(operator.tenantId(), tenant);
    }
  }

  private void appendAudit(
      TenantId tenantId, OperatorContext operator, AuditAction action, Map<String, String> details) {
    audit.append(
        new AuditEntry(
            UUID.randomUUID(),
            tenantId,
            clock.instant(),
            operator.subject(),
            operator.roles().stream().map(Enum::name).collect(Collectors.joining(",")),
            action,
            "index",
            tenantId.value(),
            operator.sourceIp(),
            details));
  }
}
