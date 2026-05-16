package com.example.security.application.port.out

import com.example.security.application.port.`in`.QueryAuditLogUseCase
import com.example.security.domain.audit.AuditEntry

/** 감사 로그 — append-only. Postgres audit_entries + (옵션) Kafka SIEM sink. */
interface AuditLogPort {

    fun append(entry: AuditEntry)

    fun query(query: QueryAuditLogUseCase.AuditQuery): List<AuditEntry>
}
