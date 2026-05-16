package com.example.security.application.port.out

import com.example.security.domain.common.TenantId
import java.util.Optional
import java.util.UUID

/**
 * idempotency-key 저장소.
 *
 * tryClaim 이 true 면 처음 본 키 — 처리를 진행. false 면 이미 처리된 키 — 기존 eventId 를
 * lookup 으로 반환.
 */
interface IdempotencyPort {

    fun tryClaim(tenantId: TenantId, key: String, eventId: UUID): Boolean

    fun lookup(tenantId: TenantId, key: String): Optional<UUID>
}
