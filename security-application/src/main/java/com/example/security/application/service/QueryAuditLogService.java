package com.example.security.application.service;

import com.example.security.application.exception.TenantMismatchException;
import com.example.security.application.port.in.OperatorContext;
import com.example.security.application.port.in.QueryAuditLogUseCase;
import com.example.security.application.port.out.AuditLogPort;
import com.example.security.domain.audit.AuditEntry;
import java.util.List;
import org.springframework.stereotype.Service;

/** use case 8 — 감사 로그 조회. */
@Service
public class QueryAuditLogService implements QueryAuditLogUseCase {

  private final AuditLogPort audit;

  public QueryAuditLogService(AuditLogPort audit) {
    this.audit = audit;
  }

  @Override
  public List<AuditEntry> query(AuditQuery query, OperatorContext operator) {
    if (!operator.canQueryOtherTenant() && !operator.tenantId().equals(query.tenantId())) {
      throw new TenantMismatchException(operator.tenantId(), query.tenantId());
    }
    return audit.query(query);
  }
}
