package com.example.security.application.port.out;

import com.example.security.application.port.in.QueryAuditLogUseCase.AuditQuery;
import com.example.security.domain.audit.AuditEntry;
import java.util.List;

/** 감사 로그 — append-only. Postgres audit_entries + (옵션) Kafka SIEM sink. */
public interface AuditLogPort {

  void append(AuditEntry entry);

  List<AuditEntry> query(AuditQuery query);
}
