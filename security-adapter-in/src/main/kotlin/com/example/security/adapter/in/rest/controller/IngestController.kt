package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.metrics.SecurityLogMetrics
import com.example.security.adapter.`in`.rest.dto.IngestRequest
import com.example.security.adapter.`in`.rest.dto.IngestResponse
import com.example.security.application.port.`in`.IngestLogEventUseCase
import com.example.security.domain.common.TenantId
import com.example.security.domain.event.RawEvent
import com.example.security.domain.mapping.EventNormalizer
import jakarta.validation.Valid
import java.time.Clock
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** use case 1 — POST /api/v1/events. */
@RestController
@RequestMapping("/api/v1/events")
class IngestController(
    private val useCase: IngestLogEventUseCase,
    private val clock: Clock,
    private val metrics: SecurityLogMetrics,
) {

    @PostMapping
    fun ingest(
        @Valid @RequestBody request: IngestRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<IngestResponse> {
        @Suppress("UNCHECKED_CAST")
        val raw = RawEvent(
            TenantId.of(request.tenantId),
            request.occurredAt ?: clock.instant(),
            request.source,
            request.schema,
            request.payload as Map<String, Any>,
        )
        try {
            val result = useCase.ingest(raw, idempotencyKey)
            metrics.recordIngest(request.source, request.tenantId, request.schema)
            val status = if (result.duplicate) HttpStatus.OK else HttpStatus.ACCEPTED
            return ResponseEntity.status(status)
                .body(IngestResponse(result.eventId, result.duplicate))
        } catch (e: EventNormalizer.UnsupportedSchemaException) {
            metrics.recordNormalizeFailure(request.source, request.schema, "unsupported_schema")
            throw e
        } catch (e: IllegalArgumentException) {
            metrics.recordNormalizeFailure(request.source, request.schema, "validation_failed")
            throw e
        }
    }
}
