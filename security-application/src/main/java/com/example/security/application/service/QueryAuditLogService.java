package com.example.security.application.service;

import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.QueryAuditLogUseCase;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.audit.AuditEntry;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/** use case 8 — 감사 로그 조회. */
@Service
public class QueryAuditLogService implements QueryAuditLogUseCase {

  private final AuditLogPort audit;
  private final Clock clock;

  public QueryAuditLogService(AuditLogPort audit, Clock clock) {
    this.audit = audit;
    this.clock = clock;
  }

  @Override
  public List<AuditEntry> query(AuditQuery query, OperatorContext operator) {
    if (!operator.canQueryOtherTenant() && !operator.tenantId().equals(query.tenantId())) {
      throw new TenantMismatchException(operator.tenantId(), query.tenantId());
    }
    // 감사 로그 자체를 본인 외 tenant 로 조회하는 것도 audit 대상 — 메타 감사.
    CrossTenantAccessAudit.recordIfCrossTenant(
        audit, clock, operator, query.tenantId(), "audit", query.tenantId().value());
    return audit.query(query);
  }
}
