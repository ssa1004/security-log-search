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
      int size) {

    /** audit 조회 페이지 크기 상한 — 무제한 dump 방지 (API4 — 자원 소비 제한). */
    public static final int MAX_SIZE = 1000;

    public AuditQuery {
      if (size < 1 || size > MAX_SIZE) {
        throw new IllegalArgumentException("size 는 1~" + MAX_SIZE + ": " + size);
      }
    }
  }
}
