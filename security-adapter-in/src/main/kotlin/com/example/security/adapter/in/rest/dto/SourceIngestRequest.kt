package com.example.security.adapter.`in`.rest.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

/**
 * source 별 raw payload ingest 요청 — CloudTrail / K8s audit 같이 schema 가 endpoint 로 고정된 경우.
 *
 * [IngestRequest] 와 달리 `schema` 필드가 없다 — endpoint 가 schema 를 결정.
 */
@JvmRecord
data class SourceIngestRequest(
    @field:NotBlank val tenantId: String,
    @field:NotNull val payload: Map<String, Any?>,
    val occurredAt: Instant?,
)
