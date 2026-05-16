package com.example.security.application.port.`in`

import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.common.TenantId
import java.time.Instant
import java.util.Optional

/**
 * use case 8 — ISMS-P 2.9 통제. 누가 언제 어떤 검색 / 룰 변경 / 알람 처리 했는지 audit 조회.
 */
interface QueryAuditLogUseCase {

    fun query(query: AuditQuery, operator: OperatorContext): List<AuditEntry>

    @JvmRecord
    data class AuditQuery(
        val tenantId: TenantId,
        val actor: Optional<String>,
        val action: Optional<AuditEntry.AuditAction>,
        val from: Optional<Instant>,
        val to: Optional<Instant>,
        val size: Int,
    ) {

        init {
            require(size in 1..MAX_SIZE) { "size 는 1~$MAX_SIZE: $size" }
        }

        companion object {
            /** audit 조회 페이지 크기 상한 — 무제한 dump 방지 (API4 — 자원 소비 제한). */
            const val MAX_SIZE: Int = 1000
        }
    }
}
