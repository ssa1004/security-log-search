package com.example.security.application.port.in;

import com.example.security.domain.audit.AuditEntry;
import com.example.security.domain.audit.AuditEntry.AuditAction;
import com.example.security.domain.common.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * use case 8 — ISMS-P 2.9 통제. 누가 언제 어떤 검색 / 룰 변경 / 알람 처리 했는지 audit 조회.
 */
public interface QueryAuditLogUseCase {

  List<AuditEntry> query(AuditQuery query, OperatorContext operator);

  record AuditQuery(
      TenantId tenantId,
      Optional<String> actor,
      Optional<AuditAction> action,
      Optional<Instant> from,
      Optional<Instant> to,
      int size) {}
}
