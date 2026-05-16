package com.example.security.adapter.out.jpa

import com.example.security.adapter.out.jpa.entity.IdempotencyKeyEntity
import com.example.security.adapter.out.jpa.repository.IdempotencyJpaRepository
import com.example.security.application.port.out.IdempotencyPort
import com.example.security.domain.common.TenantId
import java.time.Clock
import java.util.Optional
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * idempotency-key 영속.
 *
 * tryClaim 은 (tenantId, key) PK INSERT 시도 — UNIQUE 위반이 곧 중복 차단이므로 별도 락 불필요.
 * 단, 호출자 트랜잭션과 분리되어야 다른 트랜잭션이 즉시 lookup 으로 값을 볼 수 있다 (REQUIRES_NEW).
 */
@Component
class JpaIdempotencyAdapter(
    private val jpa: IdempotencyJpaRepository,
    private val clock: Clock,
) : IdempotencyPort {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun tryClaim(tenantId: TenantId, key: String, eventId: UUID): Boolean =
        try {
            jpa.save(IdempotencyKeyEntity(tenantId.value, key, eventId, clock.instant()))
            jpa.flush()
            true
        } catch (_: DataIntegrityViolationException) {
            // PK 중복 — 다른 요청이 같은 키로 먼저 INSERT 함.
            false
        }

    @Transactional(readOnly = true)
    override fun lookup(tenantId: TenantId, key: String): Optional<UUID> =
        jpa.findById(IdempotencyKeyEntity.PK(tenantId.value, key))
            .map { it.eventId }
}
