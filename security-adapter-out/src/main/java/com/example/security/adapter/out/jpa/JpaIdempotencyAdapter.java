package com.example.security.adapter.out.jpa;

import com.example.security.adapter.out.jpa.entity.IdempotencyKeyEntity;
import com.example.security.adapter.out.jpa.repository.IdempotencyJpaRepository;
import com.example.security.application.port.out.IdempotencyPort;
import com.example.security.domain.common.TenantId;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * idempotency-key 영속.
 *
 * <p>tryClaim 은 (tenantId, key) PK INSERT 시도 — UNIQUE 위반이 곧 중복 차단이므로 별도 락 불필요.
 * 단, 호출자 트랜잭션과 분리되어야 다른 트랜잭션이 즉시 lookup 으로 값을 볼 수 있다 (REQUIRES_NEW).
 */
@Component
public class JpaIdempotencyAdapter implements IdempotencyPort {

  private final IdempotencyJpaRepository jpa;
  private final Clock clock;

  public JpaIdempotencyAdapter(IdempotencyJpaRepository jpa, Clock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryClaim(TenantId tenantId, String key, UUID eventId) {
    try {
      jpa.save(new IdempotencyKeyEntity(tenantId.value(), key, eventId, clock.instant()));
      jpa.flush();
      return true;
    } catch (DataIntegrityViolationException e) {
      // PK 중복 — 다른 요청이 같은 키로 먼저 INSERT 함.
      return false;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> lookup(TenantId tenantId, String key) {
    return jpa.findById(new IdempotencyKeyEntity.PK(tenantId.value(), key))
        .map(IdempotencyKeyEntity::getEventId);
  }
}
