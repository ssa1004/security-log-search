package com.example.security.adapter.out.jpa

import com.example.security.adapter.out.jpa.entity.AuditEntryEntity
import com.example.security.adapter.out.jpa.repository.AuditJpaRepository
import com.example.security.application.port.`in`.QueryAuditLogUseCase.AuditQuery
import com.example.security.application.port.out.AuditLogPort
import com.example.security.domain.audit.AuditEntry
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * audit_entries — append-only.
 *
 * 본 어댑터는 `@Transactional` 로 INSERT 만 하고 UPDATE/DELETE 는 JpaRepository 에 노출 안
 * 한다. 보존 5년 (ISMS-P 권고) 후 별도 batch 로 archive cold storage 이관.
 */
@Component
@Transactional
class JpaAuditLogAdapter(
    private val jpa: AuditJpaRepository,
) : AuditLogPort {

    override fun append(entry: AuditEntry) {
        jpa.save(AuditEntryEntity.from(entry))
    }

    @Transactional(readOnly = true)
    override fun query(query: AuditQuery): List<AuditEntry> {
        val pageable = PageRequest.of(0, query.size)
        return jpa
            .findByFilters(
                query.tenantId.value,
                query.actor.orElse(null),
                query.action.orElse(null),
                query.from.orElse(null),
                query.to.orElse(null),
                pageable,
            )
            .map { it.toDomain() }
    }
}
