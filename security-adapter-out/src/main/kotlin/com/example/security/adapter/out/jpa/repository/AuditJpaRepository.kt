package com.example.security.adapter.out.jpa.repository

import com.example.security.adapter.out.jpa.entity.AuditEntryEntity
import com.example.security.domain.audit.AuditEntry.AuditAction
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AuditJpaRepository : JpaRepository<AuditEntryEntity, UUID> {

    @Query(
        """
        SELECT a FROM AuditEntryEntity a
        WHERE a.tenantId = :tenantId
          AND (:actor IS NULL OR a.actor = :actor)
          AND (:action IS NULL OR a.action = :action)
          AND (:from IS NULL OR a.occurredAt >= :from)
          AND (:to IS NULL OR a.occurredAt < :to)
        ORDER BY a.occurredAt DESC
        """,
    )
    fun findByFilters(
        @Param("tenantId") tenantId: String,
        @Param("actor") actor: String?,
        @Param("action") action: AuditAction?,
        @Param("from") from: Instant?,
        @Param("to") to: Instant?,
        pageable: Pageable,
    ): List<AuditEntryEntity>
}
