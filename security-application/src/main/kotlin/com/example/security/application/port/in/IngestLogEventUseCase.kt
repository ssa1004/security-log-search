package com.example.security.application.port.`in`

import com.example.security.domain.event.RawEvent
import java.util.UUID

/**
 * use case 1 — raw event 를 받아 ECS / OCSF 정규화 후 Kafka `events.normalized` topic 으로 발행.
 *
 * idempotency-key 가 동일한 요청은 한 번만 처리한다 (Postgres idempotency_keys 테이블).
 */
interface IngestLogEventUseCase {

    fun ingest(raw: RawEvent, idempotencyKey: String?): IngestResult

    /** 처리 결과. */
    @JvmRecord
    data class IngestResult(val eventId: UUID, val duplicate: Boolean)
}
