package com.example.security.adapter.`in`.rest.dto

import com.example.security.domain.audit.AuditEntry
import java.time.Instant
import java.util.UUID

/**
 * GET /api/v1/audit 응답 — audit_entries 테이블 한 row.
 *
 * details 는 자유형 key-value (예: query, denied=true, requested_tenant 등).
 */
@JvmRecord
data class AuditEntryResponse(
    val entryId: UUID,
    val tenantId: String,
    val occurredAt: Instant,
    val actor: String,
    val actorRole: String?,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val sourceIp: String?,
    val details: Map<String, String>,
) {

    companion object {
        @JvmStatic
        fun from(e: AuditEntry): AuditEntryResponse =
            AuditEntryResponse(
                e.entryId,
                e.tenantId.value,
                e.occurredAt,
                e.actor,
                e.actorRole,
                e.action.name,
                e.targetType,
                e.targetId,
                e.sourceIp,
                e.details,
            )
    }
}
