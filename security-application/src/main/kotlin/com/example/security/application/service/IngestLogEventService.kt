package com.example.security.application.service

import com.example.security.application.exception.TenantNotFoundException
import com.example.security.application.port.`in`.IngestLogEventUseCase
import com.example.security.application.port.out.AuditLogPort
import com.example.security.application.port.out.EventPublisherPort
import com.example.security.application.port.out.IdempotencyPort
import com.example.security.application.port.out.TenantRepository
import com.example.security.domain.audit.AuditEntry
import com.example.security.domain.event.RawEvent
import com.example.security.domain.mapping.EventNormalizer
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * use case 1 — raw event 정규화 + Kafka 발행 + idempotency 보장.
 *
 * 처리 순서:
 *
 *  1. tenant 존재 검증
 *  2. idempotency-key 가 있으면 lookup → 이미 본 키면 기존 eventId 반환 (duplicate=true)
 *  3. raw → ECS / OCSF 정규화
 *  4. idempotency tryClaim → 동시 다른 요청이 같은 키로 들어왔을 가능성 차단
 *  5. events.normalized 발행
 */
@Service
open class IngestLogEventService(
    private val normalizer: EventNormalizer,
    private val publisher: EventPublisherPort,
    private val idempotency: IdempotencyPort,
    private val tenants: TenantRepository,
    private val audit: AuditLogPort,
    private val clock: Clock,
) : IngestLogEventUseCase {

    override fun ingest(raw: RawEvent, idempotencyKey: String?): IngestLogEventUseCase.IngestResult {
        val tenant = tenants
            .findById(raw.tenantId)
            .orElseThrow { TenantNotFoundException(raw.tenantId) }
        if (!tenant.active) {
            throw TenantNotFoundException(raw.tenantId)
        }

        if (!idempotencyKey.isNullOrBlank()) {
            val existing = idempotency.lookup(raw.tenantId, idempotencyKey)
            if (existing.isPresent) {
                return IngestLogEventUseCase.IngestResult(existing.get(), true)
            }
        }

        val event = normalizer.normalize(raw)

        if (!idempotencyKey.isNullOrBlank()) {
            val claimed = idempotency.tryClaim(raw.tenantId, idempotencyKey, event.eventId)
            if (!claimed) {
                // 다른 요청이 동일 키로 동시에 들어왔음 — lookup 으로 그쪽 결과 반환.
                val existing = idempotency.lookup(raw.tenantId, idempotencyKey)
                if (existing.isPresent) {
                    return IngestLogEventUseCase.IngestResult(existing.get(), true)
                }
            }
        }

        publisher.publish(event)

        // 감사 로그는 빈도가 너무 높아 INGEST 자체는 audit 에 안 남김 — 대량 트래픽이라 noise.
        // 단 Tenant 비활성화 / 정규화 실패 같은 예외만 audit 가 잡는다.
        return IngestLogEventUseCase.IngestResult(event.eventId, false)
    }

    /** 운영자 도구용 — INGEST 자체는 audit 안 남기지만, 디버그용 명시 호출 시에는 남길 수 있게. */
    open fun recordIngestAudit(raw: RawEvent, eventId: UUID, actor: String) {
        audit.append(
            AuditEntry(
                UUID.randomUUID(),
                raw.tenantId,
                clock.instant(),
                actor,
                "system",
                AuditEntry.AuditAction.INGEST,
                "raw_event",
                eventId.toString(),
                null,
                mapOf("source" to raw.source, "schema" to raw.schema),
            )
        )
    }
}
