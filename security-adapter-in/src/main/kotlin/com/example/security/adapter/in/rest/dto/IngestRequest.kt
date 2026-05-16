package com.example.security.adapter.`in`.rest.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

/**
 * POST /api/v1/ingest — raw event 수집 요청.
 *
 * tenantId 는 path 가 아닌 body 에서 받는다 (multi-tenant 환경에서 운영자가 명시적으로 지정).
 */
@JvmRecord
data class IngestRequest(
    @field:NotBlank val tenantId: String,
    @field:NotBlank val source: String,
    @field:NotBlank val schema: String,
    @field:NotNull val payload: Map<String, Any?>,
    /** 클라이언트가 보고한 timestamp (없으면 서버 수신 시각 사용). */
    val occurredAt: Instant?,
)
