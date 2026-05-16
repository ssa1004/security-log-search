package com.example.security.adapter.`in`.rest.dto

import java.util.UUID

/** POST /api/v1/ingest 응답 — eventId + idempotency 중복 여부. */
@JvmRecord
data class IngestResponse(val eventId: UUID, val duplicate: Boolean)
