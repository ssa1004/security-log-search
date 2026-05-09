package com.example.security.adapter.out.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * idempotency_keys — (tenant_id, key) 복합 PK. UNIQUE 제약 위반이 곧 중복 차단.
 *
 * <p>retention 정책: 7일 후 삭제 (별도 batch). 그 이상 옛 키로 다시 들어오면 새 이벤트로 처리.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyEntity.PK.class)
public class IdempotencyKeyEntity {

  @Id
  @Column(name = "tenant_id", nullable = false, length = 32)
  private String tenantId;

  @Id
  @Column(name = "idem_key", nullable = false, length = 200)
  private String key;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IdempotencyKeyEntity() {}

  public IdempotencyKeyEntity(String tenantId, String key, UUID eventId, Instant createdAt) {
    this.tenantId = tenantId;
    this.key = key;
    this.eventId = eventId;
    this.createdAt = createdAt;
  }

  public UUID getEventId() {
    return eventId;
  }

  public static class PK implements Serializable {
    private String tenantId;
    private String key;

    public PK() {}

    public PK(String tenantId, String key) {
      this.tenantId = tenantId;
      this.key = key;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof PK pk)) return false;
      return Objects.equals(tenantId, pk.tenantId) && Objects.equals(key, pk.key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tenantId, key);
    }
  }
}
