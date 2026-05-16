package com.example.security.adapter.`in`.rest.controller

import com.example.security.adapter.`in`.metrics.SecurityLogMetrics
import com.example.security.adapter.`in`.rest.dto.IngestResponse
import com.example.security.adapter.`in`.rest.dto.SourceIngestRequest
import com.example.security.application.port.`in`.IngestLogEventUseCase
import com.example.security.domain.common.TenantId
import com.example.security.domain.event.RawEvent
import com.example.security.domain.mapping.EventNormalizer
import com.example.security.domain.mapping.source.CloudTrailToEcsMapper
import com.example.security.domain.mapping.source.K8sAuditToEcsMapper
import jakarta.validation.Valid
import java.time.Clock
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * source 전용 ingest endpoint — schema 를 endpoint path 로 고정한다.
 *
 * 일반 `POST /api/v1/events` 는 [com.example.security.adapter.in.rest.dto.IngestRequest] 의
 * `schema` 필드로 매퍼를 라우팅하지만, CloudTrail / K8s audit 처럼 *공급원 자체가 schema*
 * 인 경우는 endpoint 를 분리하면 클라이언트가 더 단순하다 (수집 agent 가 endpoint 만 알면 됨).
 *
 *  - `POST /api/v1/events/cloudtrail` → schema=`aws-cloudtrail`
 *  - `POST /api/v1/events/k8s-audit`   → schema=`k8s-audit`
 *
 * 실제 정규화는 [com.example.security.application.service.IngestLogEventService] 가 schema 를
 * 보고 적절한 매퍼로 라우팅한다.
 */
@RestController
@RequestMapping("/api/v1/events")
class SourceIngestController(
    private val useCase: IngestLogEventUseCase,
    private val clock: Clock,
    private val metrics: SecurityLogMetrics,
) {

    @PostMapping("/cloudtrail")
    fun cloudtrail(
        @Valid @RequestBody request: SourceIngestRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<IngestResponse> =
        ingestWithSchema(request, CloudTrailToEcsMapper.SCHEMA, "aws", idempotencyKey)

    @PostMapping("/k8s-audit")
    fun k8sAudit(
        @Valid @RequestBody request: SourceIngestRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<IngestResponse> =
        ingestWithSchema(request, K8sAuditToEcsMapper.SCHEMA, "k8s", idempotencyKey)

    private fun ingestWithSchema(
        request: SourceIngestRequest,
        schema: String,
        source: String,
        idempotencyKey: String?,
    ): ResponseEntity<IngestResponse> {
        @Suppress("UNCHECKED_CAST")
        val raw = RawEvent(
            TenantId.of(request.tenantId),
            request.occurredAt ?: clock.instant(),
            source,
            schema,
            request.payload as Map<String, Any>,
        )
        try {
            val result = useCase.ingest(raw, idempotencyKey)
            metrics.recordIngest(source, request.tenantId, schema)
            val status = if (result.duplicate) HttpStatus.OK else HttpStatus.ACCEPTED
            return ResponseEntity.status(status)
                .body(IngestResponse(result.eventId, result.duplicate))
        } catch (e: EventNormalizer.UnsupportedSchemaException) {
            metrics.recordNormalizeFailure(source, schema, "unsupported_schema")
            throw e
        } catch (e: IllegalArgumentException) {
            metrics.recordNormalizeFailure(source, schema, "validation_failed")
            throw e
        }
    }
}
