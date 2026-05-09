package com.example.security.application.service;

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
    var tenant = tenants.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    indexAdmin.provisionForTenant(tenant);
    appendAudit(tenantId, operator, AuditAction.INDEX_CREATED, Map.of("tenant", tenantId.value()));
  }

  @Override
  public RolloverResult triggerRollover(TenantId tenantId, OperatorContext operator) {
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
    var tenant = tenants.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));
    indexAdmin.applyIlmPolicy(tenant);
    appendAudit(
        tenantId,
        operator,
        AuditAction.ILM_POLICY_APPLIED,
        Map.of("policy", tenant.ilmPolicyName()));
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
