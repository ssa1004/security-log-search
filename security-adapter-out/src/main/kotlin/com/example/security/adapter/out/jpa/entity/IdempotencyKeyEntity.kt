package com.example.security.adapter.out.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.Objects
import java.util.UUID

/**
 * idempotency_keys — (tenant_id, key) 복합 PK. UNIQUE 제약 위반이 곧 중복 차단.
 *
 * retention 정책: 7일 후 삭제 (별도 batch). 그 이상 옛 키로 다시 들어오면 새 이벤트로 처리.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyEntity.PK::class)
class IdempotencyKeyEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 32)
    private var tenantId: String = ""

    @Id
    @Column(name = "idem_key", nullable = false, length = 200)
    private var key: String = ""

    @Column(name = "event_id", nullable = false)
    @get:JvmName("getEventId")
    var eventId: UUID = UUID(0, 0)
        private set

    @Column(name = "created_at", nullable = false)
    private var createdAt: Instant = Instant.EPOCH

    constructor()

    constructor(tenantId: String, key: String, eventId: UUID, createdAt: Instant) {
        this.tenantId = tenantId
        this.key = key
        this.eventId = eventId
        this.createdAt = createdAt
    }

    class PK : Serializable {
        var tenantId: String? = null
        var key: String? = null

        constructor()

        constructor(tenantId: String, key: String) {
            this.tenantId = tenantId
            this.key = key
        }

        override fun equals(other: Any?): Boolean {
            if (other !is PK) return false
            return Objects.equals(tenantId, other.tenantId) && Objects.equals(key, other.key)
        }

        override fun hashCode(): Int = Objects.hash(tenantId, key)
    }
}
